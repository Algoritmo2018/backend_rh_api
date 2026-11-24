package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.dto.request.TalentSearchQuery;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateSummaryResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.Candidate;
import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import com.minhaempresa.recrutamentoapi.candidate.entity.LanguageLevel;
import com.minhaempresa.recrutamentoapi.candidate.mapper.CandidateMapper;
import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepository;
import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepositoryCustom.LanguageCriterion;
import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepositoryCustom.TalentCriteria;
import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepositoryCustom.TalentHit;
import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepositoryCustom.TalentSearchPage;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateApplicationStatsContributor.ApplicationCounts;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Talent pool search (administrators and recruiters). Only candidates with an active talent-pool
 * consent are ever returned (DECISOES.md #59, decision D6-12): {@code talentPoolOnly=false} is
 * ignored. Results are summaries (no contacts nor personal data).
 */
@Service
@RequiredArgsConstructor
public class TalentSearchService {

    private final CandidateRepository repository;
    private final CandidateService candidateService;
    private final CandidateMapper mapper;
    private final ObjectProvider<CandidateApplicationStatsContributor> applicationStats;

    @Transactional(readOnly = true)
    public PageResponse<CandidateSummaryResponse> search(TalentSearchQuery query) {
        int page = query.page() == null ? 1 : query.page();
        int pageSize = query.pageSize() == null ? TalentSearchQuery.DEFAULT_PAGE_SIZE : query.pageSize();

        List<String> words = TalentSearchTerms.words(query.q());
        List<String> licenseWords = words.stream().filter(TalentSearchTerms.LICENSE_WORDS::contains).toList();
        List<String> textWords = repository.withLexemes(words.stream().filter(w -> !TalentSearchTerms.LICENSE_WORDS.contains(w)).toList());

        TalentCriteria criteria = new TalentCriteria(
                textWords,
                licenseWords,
                orEmpty(query.skills()).stream().map(Texts::fold).filter(s -> !s.isEmpty()).toList(),
                orEmpty(query.provinces()).stream().map(Enum::name).toList(),
                query.minYears(),
                query.maxYears(),
                query.minEducation() == null ? List.of() : Arrays.stream(EducationLevel.values())
                        .filter(level -> level.rank() >= query.minEducation().rank()).map(Enum::name).toList(),
                orEmpty(query.languages()).stream().map(TalentSearchService::languageCriterion).toList(),
                orEmpty(query.availability()).stream().map(Enum::name).toList(),
                new LinkedHashSet<>(orEmpty(query.tagIds())));
        TalentSearchPage result = repository.searchTalent(criteria, (long) (page - 1) * pageSize, pageSize);

        List<UUID> ids = result.hits().stream().map(TalentHit::id).toList();
        Map<UUID, Candidate> candidates = new HashMap<>();
        candidateService.load(ids).forEach(c -> candidates.put(c.getId(), c));
        Map<UUID, ApplicationCounts> counts = new HashMap<>();
        applicationStats.orderedStream().forEach(c -> c.countsByCandidate(ids).forEach((id, n) ->
                counts.merge(id, n, (a, b) -> new ApplicationCounts(a.total() + b.total(), a.active() + b.active()))));

        List<CandidateSummaryResponse> items = new ArrayList<>();
        for (TalentHit hit : result.hits()) {
            Candidate candidate = candidates.get(hit.id());
            if (candidate != null) {
                ApplicationCounts n = counts.getOrDefault(hit.id(), new ApplicationCounts(0, 0));
                items.add(mapper.toSummary(candidate, hit.relevance(), hit.matchedTerms(), n.total(), n.active()));
            }
        }
        int totalPages = (int) Math.max(1, (result.total() + pageSize - 1) / pageSize);
        return new PageResponse<>(items, result.total(), page, pageSize, totalPages);
    }

    /** {@code "Inglês:advanced"} → the language key and the levels at least as high. */
    private static LanguageCriterion languageCriterion(String value) {
        int separator = value.lastIndexOf(':');
        LanguageLevel minimum = wire(LanguageLevel.class, value.substring(separator + 1));
        List<String> levels = Arrays.stream(LanguageLevel.values()).filter(l -> l.rank() >= minimum.rank()).map(Enum::name).toList();
        return new LanguageCriterion(Texts.fold(value.substring(0, separator)), levels);
    }

    private static <E extends Enum<E> & WireEnum> E wire(Class<E> type, String value) {
        return WireEnums.fromValue(type, value);
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
