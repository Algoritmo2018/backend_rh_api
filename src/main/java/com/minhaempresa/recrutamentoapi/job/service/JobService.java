package com.minhaempresa.recrutamentoapi.job.service;

import com.minhaempresa.recrutamentoapi.client.service.ClientService;
import com.minhaempresa.recrutamentoapi.client.service.JobRequestService;
import com.minhaempresa.recrutamentoapi.job.dto.request.JobCreateRequest;
import com.minhaempresa.recrutamentoapi.job.dto.request.JobFilterRequest;
import com.minhaempresa.recrutamentoapi.job.dto.request.JobInput;
import com.minhaempresa.recrutamentoapi.job.dto.request.JobRequirementRequest;
import com.minhaempresa.recrutamentoapi.job.dto.request.JobStatusRequest;
import com.minhaempresa.recrutamentoapi.job.dto.request.JobUpdateRequest;
import com.minhaempresa.recrutamentoapi.job.dto.request.SalaryRangeRequest;
import com.minhaempresa.recrutamentoapi.job.dto.request.ScreeningQuestionRequest;
import com.minhaempresa.recrutamentoapi.job.dto.response.JobListItemResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.JobResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.JobSearchHitResponse;
import com.minhaempresa.recrutamentoapi.job.entity.Job;
import com.minhaempresa.recrutamentoapi.job.entity.JobRequirement;
import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import com.minhaempresa.recrutamentoapi.job.entity.RequirementCategory;
import com.minhaempresa.recrutamentoapi.job.entity.SalaryRange;
import com.minhaempresa.recrutamentoapi.job.entity.ScreeningQuestion;
import com.minhaempresa.recrutamentoapi.job.entity.ScreeningQuestionType;
import com.minhaempresa.recrutamentoapi.job.event.JobStaffAssignedEvent;
import com.minhaempresa.recrutamentoapi.job.event.JobStaffAssignedEvent.Assignee;
import com.minhaempresa.recrutamentoapi.job.mapper.JobMapper;
import com.minhaempresa.recrutamentoapi.job.repository.JobRepository;
import com.minhaempresa.recrutamentoapi.job.repository.JobSpecifications;
import com.minhaempresa.recrutamentoapi.job.service.JobPipelineStatsContributor.JobPipelineCounts;
import com.minhaempresa.recrutamentoapi.settings.service.TagService;
import com.minhaempresa.recrutamentoapi.shared.config.CacheNames;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ConflictException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.text.RichText;
import com.minhaempresa.recrutamentoapi.shared.text.Slugs;
import com.minhaempresa.recrutamentoapi.shared.web.PageQuery;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import com.minhaempresa.recrutamentoapi.user.service.UserReference;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jobs (vagas) for FEH OIRAM staff: list, create (also from a client job request), edit and change the
 * state. Any administrator or recruiter may edit any job.
 *
 * <p>States: {@code draft → open ⇄ paused → closed}; nothing goes back to draft and a closed job never
 * changes again. Publishing (→ open) needs an active client and a deadline that has not passed;
 * {@code publishedAt} is set on the first publication only and the slug is fixed from then on.
 *
 * <p>Every write evicts the public job caches (featured jobs, counts per area).
 */
@Service
@RequiredArgsConstructor
public class JobService {

    private static final Sort RECENTLY_UPDATED = Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"));
    private static final int SLUG_BASE_MAX = 70;

    private final JobRepository repository;
    private final JobMapper mapper;
    private final ClientService clientService;
    private final JobRequestService jobRequestService;
    private final UserService userService;
    private final TagService tagService;
    private final ObjectProvider<JobPipelineStatsContributor> pipelineStats;
    private final ApplicationEventPublisher events;
    private final JobDates jobDates;
    private final Clock clock;

    // =====================================================================================
    // Reads
    // =====================================================================================

    /** Every job matching the filters, most recently updated first (the frontend paginates). */
    @Transactional(readOnly = true)
    public List<JobListItemResponse> list(JobFilterRequest filter) {
        return toListItems(repository.findAll(specification(filter), RECENTLY_UPDATED));
    }

    /** Same as {@link #list} but one page. */
    @Transactional(readOnly = true)
    public PageResponse<JobListItemResponse> page(JobFilterRequest filter, PageQuery pageQuery) {
        Page<Job> page = repository.findAll(specification(filter), pageQuery.toPageable(RECENTLY_UPDATED));
        List<JobListItemResponse> items = toListItems(page.getContent());
        return PageResponse.from(new PageImpl<>(items, page.getPageable(), page.getTotalElements()), Function.identity());
    }

    @Transactional(readOnly = true)
    public JobListItemResponse get(UUID id) {
        return toListItems(List.of(find(id))).getFirst();
    }

    /** Global search of the top bar: title, city or client name; open jobs first. */
    @Transactional(readOnly = true)
    public List<JobSearchHitResponse> quickSearch(String term, int limit) {
        String search = term.trim();
        List<Job> jobs = repository.findAll(
                JobSpecifications.titleCityOrClient(search, clientService.idsWithNameContaining(search)),
                PageRequest.of(0, 50, RECENTLY_UPDATED)).getContent();
        Map<UUID, String> clientNames = clientService.namesOf(jobs.stream().map(Job::getClientId).toList());
        return jobs.stream()
                .sorted(Comparator.comparing((Job j) -> j.getStatus() != JobStatus.OPEN))
                .limit(limit)
                .map(j -> new JobSearchHitResponse(j.getId(), j.getTitle(), clientNames.get(j.getClientId()), j.getStatus()))
                .toList();
    }

    // =====================================================================================
    // Writes
    // =====================================================================================

    /**
     * Creates a job as a draft or published. With {@code jobRequestId} the job converts that client
     * request in the same transaction: the request is locked, must be pending, gives the client (the
     * {@code clientId} sent is ignored) and becomes {@code converted}.
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheNames.PUBLIC_JOBS_FEATURED, CacheNames.PUBLIC_JOBS_AREA_COUNTS}, allEntries = true)
    public JobResponse create(JobCreateRequest request, AuthenticatedUser actor) {
        UUID clientId = request.clientId();
        if (request.jobRequestId() != null) {
            clientId = jobRequestService.lockPendingForConversion(request.jobRequestId()).clientId();
        }
        if (request.status() != JobStatus.DRAFT && request.status() != JobStatus.OPEN) {
            throw fieldError("status", "job.status.initial");
        }
        requireNoTest(request.testId());
        requireActiveClient(clientId);
        LocalDate today = today();
        requireDeadlineNotPast(request.deadline(), today);

        Job job = new Job(clientId, request.jobRequestId());
        List<Assignee> assigned = applyStaff(job, request, actor);
        applyFields(job, request);
        job.setSlug(uniqueSlug(job.getTitle(), job.getCity(), null));
        replaceRequirements(job, request.requirements());
        replaceQuestions(job, request.screeningQuestions());
        if (request.status() == JobStatus.OPEN) {
            job.changeStatus(JobStatus.OPEN, clock.instant());
        }
        repository.save(job);
        repository.flush();

        if (request.jobRequestId() != null) {
            jobRequestService.markConverted(request.jobRequestId(), job.getId());
        }
        publishAssignments(job, assigned);
        return mapper.toResponse(job);
    }

    /**
     * Full edit ({@code PUT}). {@code version} must be the one read with the job: if someone saved in
     * between → 409. The status of the form follows the same transitions as {@link #changeStatus}.
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheNames.PUBLIC_JOBS_FEATURED, CacheNames.PUBLIC_JOBS_AREA_COUNTS}, allEntries = true)
    public JobResponse update(UUID id, JobUpdateRequest request, AuthenticatedUser actor) {
        Job job = find(id);
        if (request.version() != job.getVersion()) {
            throw new ConflictException("job.version-conflict", List.of(new FieldError("version", "job.version-conflict")));
        }
        // Edits of requirements/questions only do not dirty the job itself: bump the version anyway
        repository.forceVersionIncrement(job);

        requireNoTest(request.testId());
        LocalDate today = today();
        if (!request.clientId().equals(job.getClientId())) {
            if (job.getStatus() != JobStatus.DRAFT || job.getJobRequestId() != null) {
                throw fieldError("clientId", "job.client.locked");
            }
            requireActiveClient(request.clientId());
            job.setClientId(request.clientId());
        }
        if (!Objects.equals(request.deadline(), job.getDeadline())) {
            requireDeadlineNotPast(request.deadline(), today);
        }
        requireTransition(job.getStatus(), request.status());

        String previousTitle = job.getTitle();
        String previousCity = job.getCity();
        List<Assignee> assigned = applyStaff(job, request, actor);
        applyFields(job, request);
        if (!job.wasPublished() && !(job.getTitle().equals(previousTitle) && job.getCity().equals(previousCity))) {
            job.setSlug(uniqueSlug(job.getTitle(), job.getCity(), job.getId()));
        }
        replaceRequirements(job, request.requirements());
        replaceQuestions(job, request.screeningQuestions());
        moveTo(job, request.status(), today);
        repository.flush();

        publishAssignments(job, assigned);
        return mapper.toResponse(job);
    }

    /** Publish, pause or close (job list menu). Staying in the same state does nothing. */
    @Transactional
    @CacheEvict(cacheNames = {CacheNames.PUBLIC_JOBS_FEATURED, CacheNames.PUBLIC_JOBS_AREA_COUNTS}, allEntries = true)
    public JobResponse changeStatus(UUID id, JobStatusRequest request) {
        Job job = find(id);
        requireTransition(job.getStatus(), request.status());
        moveTo(job, request.status(), today());
        repository.flush();
        return mapper.toResponse(job);
    }

    // =====================================================================================
    // Rules
    // =====================================================================================

    private void requireTransition(JobStatus current, JobStatus target) {
        if (current.canChangeTo(target)) {
            return;
        }
        String key = current == JobStatus.CLOSED ? "job.status.closed"
                : target == JobStatus.DRAFT ? "job.status.draft"
                : "job.status.invalid";
        throw fieldError("status", key);
    }

    /** Applies an allowed transition; publishing re-checks the client and the deadline. */
    private void moveTo(Job job, JobStatus target, LocalDate today) {
        if (target == job.getStatus()) {
            return;
        }
        if (target == JobStatus.OPEN) {
            if (!clientService.isActive(job.getClientId())) {
                throw new BusinessException("job.publish.client-inactive", List.of(new FieldError("clientId", "job.publish.client-inactive")));
            }
            requireDeadlineNotPast(job.getDeadline(), today);
        }
        job.changeStatus(target, clock.instant());
    }

    private void requireActiveClient(UUID clientId) {
        if (!clientService.isActive(clientId)) {
            throw fieldError("clientId", "job.client.inactive");
        }
    }

    private static void requireDeadlineNotPast(LocalDate deadline, LocalDate today) {
        if (deadline != null && deadline.isBefore(today)) {
            throw fieldError("deadline", "job.deadline.past");
        }
    }

    /** Technical tests arrive in stage 10. */
    private static void requireNoTest(UUID testId) {
        if (testId != null) {
            throw fieldError("testId", "job.test.unavailable");
        }
    }

    /**
     * Sets the responsible recruiter and the interviewers. Only staff that are new to the job are
     * checked (active, right role): someone deactivated later stays referenced until replaced. Returns
     * the newly assigned staff to tell by email (never the actor).
     */
    private List<Assignee> applyStaff(Job job, JobInput request, AuthenticatedUser actor) {
        UUID recruiterId = request.recruiterId();
        Set<UUID> interviewers = new LinkedHashSet<>(request.interviewerIds());
        boolean recruiterChanged = !recruiterId.equals(job.getRecruiterId());
        Set<UUID> addedInterviewers = new LinkedHashSet<>(interviewers);
        addedInterviewers.removeAll(job.getInterviewerIds());

        Set<UUID> toCheck = new LinkedHashSet<>(addedInterviewers);
        if (recruiterChanged) {
            toCheck.add(recruiterId);
        }
        Map<UUID, UserReference> users = userService.referencesOf(toCheck);
        if (recruiterChanged && (users.get(recruiterId) == null || !users.get(recruiterId).isActiveRecruiter())) {
            throw fieldError("recruiterId", "job.recruiter.invalid");
        }
        for (UUID interviewerId : addedInterviewers) {
            UserReference user = users.get(interviewerId);
            if (user == null || !user.isActiveStaff()) {
                throw fieldError("interviewerIds", "job.interviewers.invalid");
            }
        }

        job.setRecruiterId(recruiterId);
        job.getInterviewerIds().retainAll(interviewers);
        job.getInterviewerIds().addAll(interviewers);

        Map<UUID, Assignee> assigned = new LinkedHashMap<>();
        if (recruiterChanged) {
            assigned.put(recruiterId, new Assignee(recruiterId, true, true));
        }
        for (UUID interviewerId : addedInterviewers) {
            assigned.putIfAbsent(interviewerId, new Assignee(interviewerId, false, users.get(interviewerId).isActiveRecruiter()));
        }
        assigned.remove(actor.id());
        return List.copyOf(assigned.values());
    }

    private void applyFields(Job job, JobInput request) {
        job.setTitle(request.title().trim());
        job.setConfidential(request.isConfidential());
        job.setArea(request.area());
        job.setProvince(request.province());
        job.setCity(request.city().trim());
        job.setWorkMode(request.workMode());
        job.setContractType(request.contractType());
        job.setExperienceLevel(request.experienceLevel());
        job.setMinExperienceYears(request.minExperienceYears());
        job.setVacancies(request.vacancies());
        job.setExpatriate(request.isExpatriate());
        job.setFeatured(request.isFeatured());
        // Sanitized before storing: the database never holds other HTML
        String html = RichText.sanitize(request.description());
        job.setDescription(html);
        job.setDescriptionText(RichText.toText(html));
        job.setResponsibilities(trimmed(request.responsibilities()));
        job.setBenefits(trimmed(request.benefits()));
        job.setSalary(toSalary(request.salary()));
        job.setDeadline(request.deadline());

        Set<UUID> tagIds = new LinkedHashSet<>(request.tagIds() == null ? List.of() : request.tagIds());
        if (!tagService.existingIds(tagIds).containsAll(tagIds)) {
            throw fieldError("tagIds", "job.tags.invalid");
        }
        job.getTagIds().retainAll(tagIds);
        job.getTagIds().addAll(tagIds);
    }

    /**
     * Child-collection rule: a UUID of a requirement of this job updates it, anything else creates one,
     * and requirements missing from the list are deleted. The position is the order in the list.
     */
    private static void replaceRequirements(Job job, List<JobRequirementRequest> items) {
        Map<UUID, JobRequirement> existing = new HashMap<>();
        job.getRequirements().forEach(r -> existing.put(r.getId(), r));
        int position = 0;
        for (JobRequirementRequest item : items == null ? List.<JobRequirementRequest>of() : items) {
            UUID id = parseUuid(item.id());
            JobRequirement requirement = id == null ? null : existing.remove(id);
            if (requirement == null) {
                requirement = job.addRequirement();
            }
            requirement.setPosition(position++);
            requirement.setDescription(item.description().trim());
            requirement.setType(item.type());
            requirement.setWeight(item.weight());
            requirement.setCategory(item.category());
            requirement.setValue(blankToNull(item.value()));
            boolean hasMinimum = item.category() == RequirementCategory.EXPERIENCE || item.category() == RequirementCategory.LANGUAGE;
            requirement.setMinValue(hasMinimum ? item.minValue() : null);
        }
        job.getRequirements().removeAll(existing.values());
        job.getRequirements().sort(Comparator.comparingInt(JobRequirement::getPosition));
    }

    /** Same rule as {@link #replaceRequirements}; options only for choice questions. */
    private static void replaceQuestions(Job job, List<ScreeningQuestionRequest> items) {
        Map<UUID, ScreeningQuestion> existing = new HashMap<>();
        job.getScreeningQuestions().forEach(q -> existing.put(q.getId(), q));
        int position = 0;
        for (ScreeningQuestionRequest item : items == null ? List.<ScreeningQuestionRequest>of() : items) {
            UUID id = parseUuid(item.id());
            ScreeningQuestion question = id == null ? null : existing.remove(id);
            if (question == null) {
                question = job.addScreeningQuestion();
            }
            boolean choice = item.type() == ScreeningQuestionType.SINGLE_CHOICE || item.type() == ScreeningQuestionType.MULTIPLE_CHOICE;
            question.setPosition(position++);
            question.setQuestion(item.question().trim());
            question.setType(item.type());
            question.setOptions(choice ? trimmed(item.options()) : new ArrayList<>());
            question.setRequired(item.required());
            question.setEliminatory(item.eliminatory());
            question.setExpectedAnswer(item.eliminatory() ? blankToNull(item.expectedAnswer()) : null);
        }
        job.getScreeningQuestions().removeAll(existing.values());
        job.getScreeningQuestions().sort(Comparator.comparingInt(ScreeningQuestion::getPosition));
    }

    /** {@code title-city}, with {@code -2}, {@code -3}… when taken by another job. */
    private String uniqueSlug(String title, String city, UUID ignoreId) {
        String base = Slugs.slugify(title + " " + city, SLUG_BASE_MAX);
        if (base.isEmpty()) {
            base = "vaga";
        }
        String slug = base;
        for (int n = 2; ignoreId == null ? repository.existsBySlug(slug) : repository.existsBySlugAndIdNot(slug, ignoreId); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    private void publishAssignments(Job job, List<Assignee> assigned) {
        if (!assigned.isEmpty()) {
            events.publishEvent(new JobStaffAssignedEvent(job.getId(), job.getTitle(), assigned));
        }
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private Specification<Job> specification(JobFilterRequest filter) {
        String search = filter.search() == null || filter.search().isBlank() ? null : filter.search().trim();
        List<UUID> matchingClients = search == null ? List.of() : clientService.idsWithNameContaining(search);
        return JobSpecifications.status(filter.status())
                .and(JobSpecifications.clientId(filter.clientId()))
                .and(JobSpecifications.recruiterId(filter.recruiterId()))
                .and(JobSpecifications.titleOrClient(search, matchingClients));
    }

    private List<JobListItemResponse> toListItems(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = jobs.stream().map(Job::getId).toList();
        Map<UUID, String> clientNames = clientService.namesOf(jobs.stream().map(Job::getClientId).toList());
        Map<UUID, UserReference> recruiters = userService.referencesOf(jobs.stream().map(Job::getRecruiterId).toList());
        Map<UUID, JobPipelineCounts> pipeline = pipelineCounts(ids);
        return jobs.stream().map(job -> {
            UserReference recruiter = recruiters.get(job.getRecruiterId());
            JobPipelineCounts counts = pipeline.get(job.getId());
            Map<String, Long> stageCounts = new LinkedHashMap<>();
            for (PipelineStageKey stage : PipelineStageKey.values()) {
                stageCounts.put(stage.getValue(), counts == null ? 0L : counts.stageCounts().getOrDefault(stage, 0L));
            }
            return new JobListItemResponse(
                    mapper.toResponse(job),
                    clientNames.get(job.getClientId()),
                    recruiter == null ? null : recruiter.name(),
                    recruiter != null && recruiter.active(),
                    stageCounts,
                    counts == null ? 0 : counts.totalCandidates(),
                    counts == null ? 0 : counts.activeCandidates(),
                    counts == null ? 0 : counts.rejectedCount());
        }).toList();
    }

    /** Sum over the contributors (none until the applications module exists). */
    private Map<UUID, JobPipelineCounts> pipelineCounts(Collection<UUID> jobIds) {
        Map<UUID, JobPipelineCounts> total = new HashMap<>();
        pipelineStats.orderedStream().forEach(contributor -> contributor.countsByJob(jobIds).forEach((id, counts) -> total.merge(id, counts, JobService::sum)));
        return total;
    }

    private static JobPipelineCounts sum(JobPipelineCounts a, JobPipelineCounts b) {
        Map<PipelineStageKey, Long> stages = new HashMap<>(a.stageCounts());
        b.stageCounts().forEach((stage, n) -> stages.merge(stage, n, Long::sum));
        return new JobPipelineCounts(stages, a.totalCandidates() + b.totalCandidates(), a.activeCandidates() + b.activeCandidates(), a.rejectedCount() + b.rejectedCount());
    }

    private LocalDate today() {
        return jobDates.today();
    }

    private Job find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("job.not-found"));
    }

    private static SalaryRange toSalary(SalaryRangeRequest salary) {
        if (salary == null) {
            return null;
        }
        return new SalaryRange(salary.min(), salary.max(), salary.currency(), salary.period(), salary.visible());
    }

    private static List<String> trimmed(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            values.stream().map(String::trim).forEach(result::add);
        }
        return result;
    }

    private static BusinessException fieldError(String field, String key) {
        return new BusinessException(key, List.of(new FieldError(field, key)));
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
