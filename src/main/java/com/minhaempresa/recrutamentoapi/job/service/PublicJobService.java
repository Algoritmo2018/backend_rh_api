package com.minhaempresa.recrutamentoapi.job.service;

import com.minhaempresa.recrutamentoapi.client.service.ClientService;
import com.minhaempresa.recrutamentoapi.job.dto.request.PublicJobQuery;
import com.minhaempresa.recrutamentoapi.job.dto.request.PublicJobSort;
import com.minhaempresa.recrutamentoapi.job.dto.response.MatchableJobResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.PublicJobResponse;
import com.minhaempresa.recrutamentoapi.job.entity.Job;
import com.minhaempresa.recrutamentoapi.job.mapper.JobMapper;
import com.minhaempresa.recrutamentoapi.job.repository.JobRepository;
import com.minhaempresa.recrutamentoapi.job.repository.JobRepositoryCustom.PublicSearch;
import com.minhaempresa.recrutamentoapi.job.repository.JobRepositoryCustom.PublicSearchPage;
import com.minhaempresa.recrutamentoapi.shared.config.CacheNames;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jobs on the public portal. Visible = {@code open} and the deadline (if any) is today or later, in
 * Angola time. Responses never contain internal data (see {@link PublicJobResponse}).
 *
 * <p>Featured jobs and counts per area are cached ({@link CacheNames}) per day (the key has today's date
 * in Angola, so expired jobs leave at midnight); {@link JobService} evicts them on every job change and
 * {@code PublicJobCacheListener} on every client change (company names).
 */
@Service
@RequiredArgsConstructor
public class PublicJobService {

    /** Words of the search: letters and digits only, so the {@code to_tsquery} text is always valid. */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final int MAX_SEARCH_WORDS = 8;
    private static final int MATCHABLE_LIMIT = 200;

    private final JobRepository repository;
    private final JobMapper mapper;
    private final ClientService clientService;
    private final JobDates jobDates;
    private final Clock clock;

    /**
     * Search with filters. The text matches title (most relevant), city and description; accents are
     * ignored and each word matches as a prefix ("logis" finds "Logística"). All words must match.
     */
    @Transactional(readOnly = true)
    public PageResponse<PublicJobResponse> search(PublicJobQuery query) {
        int page = query.page() == null ? 1 : query.page();
        int pageSize = query.pageSize() == null ? PublicJobQuery.DEFAULT_PAGE_SIZE : query.pageSize();
        Instant publishedSince = query.postedWithinDays() == null ? null : clock.instant().minus(Duration.ofDays(query.postedWithinDays()));
        PublicSearch search = new PublicSearch(
                today(),
                toTsQuery(query.q()),
                query.province(),
                query.area(),
                query.contractType(),
                query.workMode(),
                query.experienceLevel(),
                publishedSince,
                query.sort() == null ? PublicJobSort.RECENT : query.sort());
        PublicSearchPage result = repository.searchPublic(search, (long) (page - 1) * pageSize, pageSize);

        Map<UUID, Integer> order = new LinkedHashMap<>();
        result.ids().forEach(id -> order.put(id, order.size()));
        List<Job> jobs = new ArrayList<>(repository.findAllById(result.ids()));
        jobs.sort(Comparator.comparingInt(job -> order.get(job.getId())));

        int totalPages = (int) Math.max(1, (result.total() + pageSize - 1) / pageSize);
        return new PageResponse<>(toPublic(jobs), result.total(), page, pageSize, totalPages);
    }

    /** Featured visible jobs, newest first. The key has today's date: a job that expired leaves at midnight. */
    @Cacheable(cacheNames = CacheNames.PUBLIC_JOBS_FEATURED, key = "{@jobDates.today(), #limit}")
    @Transactional(readOnly = true)
    public List<PublicJobResponse> featured(int limit) {
        return List.copyOf(toPublic(repository.findFeatured(today(), PageRequest.ofSize(limit))));
    }

    /** Visible jobs per area, keyed by the area value ({@code oil_gas}); areas without jobs are absent. */
    @Cacheable(cacheNames = CacheNames.PUBLIC_JOBS_AREA_COUNTS, key = "@jobDates.today()")
    @Transactional(readOnly = true)
    public Map<String, Long> areaCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        repository.countVisibleByArea(today()).stream()
                .sorted(Comparator.comparing(JobRepository.AreaCount::getArea))
                .forEach(c -> counts.put(c.getArea().getValue(), c.getTotal()));
        return Map.copyOf(counts);
    }

    /**
     * The most recent visible jobs (at most {@value #MATCHABLE_LIMIT}) with their full requirements, for
     * the match score of recommended jobs. Never returned as such to a candidate.
     */
    @Transactional(readOnly = true)
    public List<MatchableJobResponse> matchableJobs() {
        List<Job> jobs = repository.findVisible(today(), PageRequest.ofSize(MATCHABLE_LIMIT));
        List<PublicJobResponse> visible = toPublic(jobs);
        List<MatchableJobResponse> result = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            result.add(new MatchableJobResponse(visible.get(i), jobs.get(i).getRequirements().stream().map(mapper::toRequirementResponse).toList()));
        }
        return result;
    }

    /** A visible job by slug; anything else (draft, paused, closed, deadline passed) → 404. */
    @Transactional(readOnly = true)
    public PublicJobResponse getBySlug(String slug) {
        Job job = repository.findPubliclyVisibleBySlug(slug, today()).orElseThrow(() -> new NotFoundException("job.not-found"));
        return toPublic(List.of(job)).getFirst();
    }

    private List<PublicJobResponse> toPublic(List<Job> jobs) {
        List<UUID> namedClients = jobs.stream().filter(j -> !j.isConfidential()).map(Job::getClientId).distinct().toList();
        Map<UUID, String> names = clientService.namesOf(namedClients);
        return jobs.stream().map(job -> mapper.toPublic(job, names.get(job.getClientId()))).toList();
    }

    /** {@code "técnico hse"} → {@code "técnico:* & hse:*"} ({@code null} when there is no word). */
    static String toTsQuery(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = WORD.matcher(text);
        List<String> words = new ArrayList<>();
        while (matcher.find() && words.size() < MAX_SEARCH_WORDS) {
            words.add(matcher.group().toLowerCase(Locale.ROOT) + ":*");
        }
        return words.isEmpty() ? null : words.stream().collect(Collectors.joining(" & "));
    }

    private LocalDate today() {
        return jobDates.today();
    }
}
