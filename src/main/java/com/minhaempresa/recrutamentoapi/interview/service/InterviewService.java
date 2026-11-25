package com.minhaempresa.recrutamentoapi.interview.service;

import com.minhaempresa.recrutamentoapi.application.entity.ApplicationStatus;
import com.minhaempresa.recrutamentoapi.application.service.InterviewTarget;
import com.minhaempresa.recrutamentoapi.application.service.PipelineService;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateContact;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.interview.dto.request.ChooseSlotRequest;
import com.minhaempresa.recrutamentoapi.interview.dto.request.InterviewCreateRequest;
import com.minhaempresa.recrutamentoapi.interview.dto.request.InterviewFilterRequest;
import com.minhaempresa.recrutamentoapi.interview.dto.request.InterviewStatusRequest;
import com.minhaempresa.recrutamentoapi.interview.dto.request.InterviewUpdateRequest;
import com.minhaempresa.recrutamentoapi.interview.dto.response.InterviewResponse;
import com.minhaempresa.recrutamentoapi.interview.entity.Interview;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewMode;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewStatus;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewType;
import com.minhaempresa.recrutamentoapi.interview.entity.MeetingProvider;
import com.minhaempresa.recrutamentoapi.interview.event.InterviewChangedEvent;
import com.minhaempresa.recrutamentoapi.interview.event.InterviewChangedEvent.Change;
import com.minhaempresa.recrutamentoapi.interview.mapper.InterviewMapper;
import com.minhaempresa.recrutamentoapi.interview.repository.InterviewRepository;
import com.minhaempresa.recrutamentoapi.interview.repository.InterviewRepository.ConflictRow;
import com.minhaempresa.recrutamentoapi.interview.repository.InterviewSpecifications;
import com.minhaempresa.recrutamentoapi.job.service.ApplicableJob;
import com.minhaempresa.recrutamentoapi.job.service.JobService;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ConflictException;
import com.minhaempresa.recrutamentoapi.shared.exception.ForbiddenException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserContact;
import com.minhaempresa.recrutamentoapi.user.service.UserReference;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interviews of the internal panel (stage 9a).
 *
 * <ul>
 *   <li><b>Who:</b> administrators and recruiters schedule, edit, change the status and fix a slot; an interviewer
 *       only reads their own interviews (frontend {@code canManage}).
 *   <li><b>Schedule:</b> a fixed future time, or 2–5 future slots for the candidate (or, for a client interview, the
 *       client) to choose (A1). The application must be in progress; a client interview needs it shared with the
 *       client (8c). Interviewers: 1–10 active staff. Video links only on the provider's host (A3).
 *   <li><b>Statuses:</b> {@code pending_slot → scheduled | cancelled}; {@code scheduled → scheduled (rescheduled) |
 *       done | no_show | cancelled}; {@code done}, {@code no_show} and {@code cancelled} are final, the first two
 *       only from the start time. Edits and status changes carry the {@code version} (stale → 409).
 *   <li><b>Conflicts</b> (D9-2): an interviewer or the candidate with an overlapping scheduled interview → 409 with
 *       the conflicts, unless {@code ignoreConflicts}.
 *   <li><b>Notices</b> ({@link InterviewChangedEvent}): candidate (invitation with {@code .ics}), interviewers and
 *       client participants, in the same transaction.
 *   <li><b>Automatic cancellation</b> (D9-10): interviews still to happen of rejected applications and of closed jobs.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final Set<InterviewStatus> OPEN = EnumSet.of(InterviewStatus.PENDING_SLOT, InterviewStatus.SCHEDULED);
    private static final int MIN_SLOTS = 2;
    private static final int MAX_SLOTS = 5;
    /** Excluded from the conflict search when there is no interview yet. */
    private static final UUID NO_INTERVIEW = new UUID(0, 0);

    private final InterviewRepository repository;
    private final InterviewMapper mapper;
    private final PipelineService pipelineService;
    private final JobService jobService;
    private final CandidateService candidateService;
    private final UserService userService;
    private final InterviewTexts texts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // =====================================================================================
    // Reads
    // =====================================================================================

    /** Agenda, by start time (waiting for a slot at the end). An interviewer only gets their own. */
    @Transactional(readOnly = true)
    public List<InterviewResponse> list(InterviewFilterRequest filter, AuthenticatedUser viewer) {
        UUID interviewerId = isInterviewer(viewer) ? viewer.id() : filter.interviewerId();
        List<UUID> jobIds = filter.clientId() == null ? null : jobService.jobIdsOfClient(filter.clientId());
        List<Interview> interviews = repository.findAll(
                InterviewSpecifications.matching(filter.from(), filter.to(), interviewerId, filter.candidateId(), filter.applicationId(), filter.jobId(), jobIds,
                        filter.status()),
                Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("createdAt")));
        return responses(interviews);
    }

    @Transactional(readOnly = true)
    public InterviewResponse get(UUID id, AuthenticatedUser viewer) {
        Interview interview = find(id);
        if (isInterviewer(viewer) && !interview.getInterviewerIds().contains(viewer.id())) {
            throw new ForbiddenException("interview.not-assigned");
        }
        return responses(List.of(interview)).getFirst();
    }

    // =====================================================================================
    // Schedule
    // =====================================================================================

    @Transactional
    public InterviewResponse create(InterviewCreateRequest request, AuthenticatedUser actor) {
        InterviewTarget application = pipelineService.interviewTarget(request.applicationId());
        if (application.status() != ApplicationStatus.ACTIVE) {
            throw new BusinessException("interview.application-not-active", List.of(new FieldError("applicationId", "interview.application-not-active")));
        }
        if (request.type() == InterviewType.CLIENT && !application.visibleToClient()) {
            throw new BusinessException("interview.client-not-shared", List.of(new FieldError("type", "interview.client-not-shared")));
        }
        ApplicableJob job = job(application.jobId());
        Interview interview = new Interview(application.applicationId(), application.jobId(), application.candidateId(), request.type());
        interview.setDurationMinutes(request.durationMinutes());
        applyPlace(interview, request.mode(), request.location(), request.meetingProvider(), request.meetingUrl());
        interview.replaceInterviewers(activeStaff(request.interviewerIds()));
        interview.replaceClientParticipants(participants(request.type(), job.clientId(), request.clientParticipantIds()));
        interview.setNotes(trimmedOrNull(request.notes()));
        if (request.startsAt() != null) {
            requireFuture(request.startsAt(), "startsAt", "interview.starts-at.past");
            interview.scheduleAt(request.startsAt());
        } else if (request.proposedSlots() != null && !request.proposedSlots().isEmpty()) {
            interview.proposeSlots(validSlots(request.proposedSlots()));
        } else {
            throw new BusinessException("interview.time-required", List.of(new FieldError("startsAt", "interview.time-required")));
        }
        checkConflicts(interview, NO_INTERVIEW, Boolean.TRUE.equals(request.ignoreConflicts()));
        repository.saveAndFlush(interview);
        Change change = interview.getStatus() == InterviewStatus.SCHEDULED ? Change.SCHEDULED : Change.SLOTS_PROPOSED;
        publish(interview, job, change, actor.id(), Boolean.TRUE.equals(request.notifyCandidate()), true,
                union(interview.getInterviewerIds(), interview.getClientParticipantIds()), Set.of(), false);
        return responses(List.of(interview)).getFirst();
    }

    /**
     * Reschedules or edits an interview still to happen. A new time, place, link or duration is a change the
     * calendars see (new {@code SEQUENCE}); added people are invited, removed ones get the cancellation.
     */
    @Transactional
    public InterviewResponse update(UUID id, InterviewUpdateRequest request, AuthenticatedUser actor) {
        Interview interview = find(id);
        requireVersion(interview, request.version());
        requireOpen(interview);
        ApplicableJob job = job(interview.getJobId());
        InterviewStatus statusBefore = interview.getStatus();
        String scheduleBefore = scheduleKey(interview);
        Set<UUID> interviewersBefore = Set.copyOf(interview.getInterviewerIds());
        Set<UUID> participantsBefore = Set.copyOf(interview.getClientParticipantIds());

        if (request.durationMinutes() != null) {
            interview.setDurationMinutes(request.durationMinutes());
        }
        InterviewMode mode = request.mode() != null ? request.mode() : interview.getMode();
        boolean modeChanged = mode != interview.getMode();
        applyPlace(interview, mode,
                request.location() != null || modeChanged ? request.location() : interview.getLocation(),
                request.meetingProvider() != null || modeChanged ? request.meetingProvider() : interview.getMeetingProvider(),
                request.meetingUrl() != null || modeChanged ? request.meetingUrl() : interview.getMeetingUrl());
        if (request.startsAt() != null) {
            requireFuture(request.startsAt(), "startsAt", "interview.starts-at.past");
            interview.scheduleAt(request.startsAt());
        } else if (request.proposedSlots() != null && !request.proposedSlots().isEmpty()) {
            interview.proposeSlots(validSlots(request.proposedSlots()));
        }
        if (request.interviewerIds() != null) {
            interview.replaceInterviewers(activeStaff(request.interviewerIds()));
        }
        if (request.clientParticipantIds() != null && interview.getType() == InterviewType.CLIENT) {
            interview.replaceClientParticipants(participants(InterviewType.CLIENT, job.clientId(), request.clientParticipantIds()));
        }
        if (request.notes() != null) {
            interview.setNotes(trimmedOrNull(request.notes()));
        }

        boolean scheduleChanged = !scheduleBefore.equals(scheduleKey(interview));
        Set<UUID> added = difference(union(interview.getInterviewerIds(), interview.getClientParticipantIds()), union(interviewersBefore, participantsBefore));
        Set<UUID> removed = difference(union(interviewersBefore, participantsBefore), union(interview.getInterviewerIds(), interview.getClientParticipantIds()));
        if (scheduleChanged || !added.isEmpty()) {
            checkConflicts(interview, interview.getId(), Boolean.TRUE.equals(request.ignoreConflicts()));
        }
        if (scheduleChanged || !removed.isEmpty()) {
            interview.bumpCalendarSequence();
        }
        repository.flush();
        if (scheduleChanged || !added.isEmpty() || !removed.isEmpty()) {
            Change change = interview.getStatus() == InterviewStatus.PENDING_SLOT ? Change.SLOTS_PROPOSED
                    : statusBefore == InterviewStatus.PENDING_SLOT ? Change.SLOT_CHOSEN
                    : Change.RESCHEDULED;
            publish(interview, job, change, actor.id(), Boolean.TRUE.equals(request.notifyCandidate()) && scheduleChanged, scheduleChanged, added, removed, false);
        }
        return responses(List.of(interview)).getFirst();
    }

    /** {@code done}, {@code no_show} (from the start time) or {@code cancelled} (notifying the candidate if asked). */
    @Transactional
    public InterviewResponse changeStatus(UUID id, InterviewStatusRequest request, AuthenticatedUser actor) {
        Interview interview = find(id);
        requireVersion(interview, request.version());
        requireOpen(interview);
        InterviewStatus target = request.status();
        switch (target) {
            case DONE, NO_SHOW -> {
                if (interview.getStatus() != InterviewStatus.SCHEDULED || interview.getStartsAt().isAfter(clock.instant())) {
                    throw new BusinessException("interview.status.too-early", List.of(new FieldError("status", "interview.status.too-early")));
                }
                interview.setStatus(target);
            }
            case CANCELLED -> {
                interview.setStatus(InterviewStatus.CANCELLED);
                interview.bumpCalendarSequence();
            }
            default -> throw new BusinessException("interview.status.invalid", List.of(new FieldError("status", "interview.status.invalid")));
        }
        if (request.notes() != null) {
            interview.setNotes(trimmedOrNull(request.notes()));
        }
        repository.flush();
        if (target == InterviewStatus.CANCELLED) {
            publish(interview, job(interview.getJobId()), Change.CANCELLED, actor.id(), Boolean.TRUE.equals(request.notifyCandidate()), true, Set.of(), Set.of(),
                    false);
        }
        return responses(List.of(interview)).getFirst();
    }

    /** Staff "Fixar": one of the proposed slots, as if the candidate (or the client) had chosen it. */
    @Transactional
    public InterviewResponse chooseSlotAsStaff(UUID id, ChooseSlotRequest request, AuthenticatedUser actor) {
        return responses(List.of(chooseSlot(id, request.slot(), actor.id(), true, Boolean.TRUE.equals(request.ignoreConflicts())))).getFirst();
    }

    /**
     * Chooses one of the proposed slots (A1). The interview is locked: the first choice wins, the next one gets 409
     * "O horário já foi escolhido.". A slot in the past → 422. When an interviewer or the candidate became busy at that
     * time: staff → 409 with the conflicts (unless {@code ignoreConflicts}); candidate/client → 422 "já não está
     * disponível". The candidate always gets the invitation.
     *
     * @param chooserId who chose ({@code null} = the candidate, stage 9c)
     */
    @Transactional
    public Interview chooseSlot(UUID id, Instant slot, UUID chooserId, boolean staff, boolean ignoreConflicts) {
        Interview interview = repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("interview.not-found"));
        if (interview.getStatus() != InterviewStatus.PENDING_SLOT) {
            throw new ConflictException("interview.slot-taken");
        }
        if (interview.getProposedSlots().stream().noneMatch(slot::equals)) {
            throw new BusinessException("interview.slot.unavailable", List.of(new FieldError("slot", "interview.slot.unavailable")));
        }
        if (!slot.isAfter(clock.instant())) {
            throw new BusinessException("interview.slot.past", List.of(new FieldError("slot", "interview.slot.past")));
        }
        List<Instant> proposed = List.copyOf(interview.getProposedSlots());
        interview.scheduleAt(slot);
        if (staff) {
            checkConflicts(interview, interview.getId(), ignoreConflicts);
        } else if (!conflicts(interview, interview.getId()).isEmpty()) {
            interview.proposeSlots(proposed);
            throw new BusinessException("interview.slot.unavailable", List.of(new FieldError("slot", "interview.slot.unavailable")));
        }
        interview.bumpCalendarSequence();
        repository.flush();
        publish(interview, job(interview.getJobId()), Change.SLOT_CHOSEN, chooserId, true, true, Set.of(), Set.of(), false);
        return interview;
    }

    // =====================================================================================
    // Automatic cancellation (D9-10) and erasure (D9-11)
    // =====================================================================================

    /** Interviews still to happen of these applications (rejected, withdrawn): cancelled, everyone told. */
    @Transactional
    public int cancelForApplications(Collection<UUID> applicationIds, UUID actorId) {
        return cancel(repository.findByApplicationIdInAndStatusIn(applicationIds, OPEN), actorId, false);
    }

    /** Interviews still to happen of a closed job. */
    @Transactional
    public int cancelForJob(UUID jobId, UUID actorId) {
        return cancel(repository.findByJobIdAndStatusIn(jobId, OPEN), actorId, false);
    }

    /**
     * Erasure of a candidate: interviews still to happen are cancelled (interviewers and client told without the name,
     * no message to the candidate), the notes of every interview are blanked; the rows stay for statistics.
     */
    @Transactional
    public void eraseCandidate(UUID candidateId) {
        List<Interview> interviews = repository.findByCandidateId(candidateId);
        cancel(interviews.stream().filter(i -> OPEN.contains(i.getStatus())).toList(), null, true);
        interviews.forEach(i -> i.setNotes(null));
        repository.flush();
    }

    private int cancel(List<Interview> interviews, UUID actorId, boolean anonymous) {
        Instant now = clock.instant();
        int cancelled = 0;
        for (Interview interview : interviews) {
            // A scheduled interview whose time passed (result not recorded yet) is left for the staff
            if (interview.getStatus() == InterviewStatus.SCHEDULED && !interview.getStartsAt().isAfter(now)) {
                continue;
            }
            interview.setStatus(InterviewStatus.CANCELLED);
            interview.bumpCalendarSequence();
            repository.flush();
            publish(interview, job(interview.getJobId()), Change.CANCELLED, actorId, !anonymous, true, Set.of(), Set.of(), anonymous);
            cancelled++;
        }
        return cancelled;
    }

    // =====================================================================================
    // Access (other modules)
    // =====================================================================================

    @Transactional(readOnly = true)
    public Set<UUID> candidateIdsOfInterviewer(UUID interviewerId) {
        return Set.copyOf(repository.findCandidateIdsByInterviewer(interviewerId));
    }

    @Transactional(readOnly = true)
    public Set<UUID> applicationIdsOfInterviewer(UUID interviewerId) {
        return Set.copyOf(repository.findApplicationIdsByInterviewer(interviewerId));
    }

    // =====================================================================================
    // Validation helpers
    // =====================================================================================

    /** Keeps only the fields of the mode: onsite → address; video → provider and link on its host (A3); phone → none. */
    private static void applyPlace(Interview interview, InterviewMode mode, String location, MeetingProvider provider, String meetingUrl) {
        interview.setMode(mode);
        switch (mode) {
            case ONSITE -> {
                String address = trimmedOrNull(location);
                if (address == null || address.length() < 3) {
                    throw new BusinessException("interview.location.required", List.of(new FieldError("location", "interview.location.required")));
                }
                interview.setLocation(address);
                interview.setMeetingProvider(null);
                interview.setMeetingUrl(null);
            }
            case VIDEO -> {
                if (provider == null) {
                    throw new BusinessException("interview.meeting-provider.required", List.of(new FieldError("meetingProvider", "interview.meeting-provider.required")));
                }
                interview.setMeetingProvider(provider);
                interview.setMeetingUrl(validMeetingUrl(provider, meetingUrl));
                interview.setLocation(null);
            }
            case PHONE -> {
                interview.setLocation(null);
                interview.setMeetingProvider(null);
                interview.setMeetingUrl(null);
            }
        }
    }

    /** {@code https} on {@code meet.google.com} (Meet) or {@code teams.microsoft.com} / {@code teams.live.com} (Teams). */
    static String validMeetingUrl(MeetingProvider provider, String value) {
        String url = trimmedOrNull(value);
        if (url == null) {
            throw new BusinessException("interview.meeting-url.invalid", List.of(new FieldError("meetingUrl", "interview.meeting-url.invalid")));
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean hostOk = switch (provider) {
                case MEET -> host.equals("meet.google.com");
                case TEAMS -> host.equals("teams.microsoft.com") || host.equals("teams.live.com");
            };
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !hostOk || uri.getUserInfo() != null || url.chars().anyMatch(Character::isWhitespace)) {
                throw new URISyntaxException(url, "host");
            }
            return url;
        } catch (URISyntaxException e) {
            throw new BusinessException("interview.meeting-url.invalid." + provider.getValue(),
                    List.of(new FieldError("meetingUrl", "interview.meeting-url.invalid." + provider.getValue())));
        }
    }

    private Set<UUID> activeStaff(List<UUID> ids) {
        Set<UUID> unique = new LinkedHashSet<>(ids);
        Map<UUID, UserReference> users = userService.referencesOf(unique);
        if (unique.stream().anyMatch(id -> users.get(id) == null || !users.get(id).isActiveStaff())) {
            throw new BusinessException("interview.interviewers.invalid", List.of(new FieldError("interviewerIds", "interview.interviewers.invalid")));
        }
        return unique;
    }

    /** Client interview: active portal users of the job's company (all of them when none is chosen); other types: none. */
    private Set<UUID> participants(InterviewType type, UUID clientId, List<UUID> requested) {
        if (type != InterviewType.CLIENT) {
            return Set.of();
        }
        Set<UUID> active = userService.activeContactsOfClient(clientId).stream().map(UserContact::id).collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested == null || requested.isEmpty()) {
            return active;
        }
        Set<UUID> chosen = new LinkedHashSet<>(requested);
        if (!active.containsAll(chosen)) {
            throw new BusinessException("interview.participants.invalid", List.of(new FieldError("clientParticipantIds", "interview.participants.invalid")));
        }
        return chosen;
    }

    /** 2–5 different future slots, in time order. */
    private List<Instant> validSlots(List<Instant> slots) {
        Set<Instant> unique = new java.util.TreeSet<>(slots);
        if (unique.size() < MIN_SLOTS || unique.size() > MAX_SLOTS || unique.size() != slots.size()) {
            throw new BusinessException("interview.slots.count", List.of(new FieldError("proposedSlots", "interview.slots.count")));
        }
        if (unique.stream().anyMatch(s -> !s.isAfter(clock.instant()))) {
            throw new BusinessException("interview.slots.past", List.of(new FieldError("proposedSlots", "interview.slots.past")));
        }
        return List.copyOf(unique);
    }

    private void requireFuture(Instant time, String field, String key) {
        if (!time.isAfter(clock.instant())) {
            throw new BusinessException(key, List.of(new FieldError(field, key)));
        }
    }

    /** A scheduled interview overlapping another one of the same interviewers or candidate → 409 with the list. */
    private void checkConflicts(Interview interview, UUID exceptId, boolean ignore) {
        if (ignore) {
            return;
        }
        List<ConflictRow> rows = conflicts(interview, exceptId);
        if (rows.isEmpty()) {
            return;
        }
        Map<UUID, String> names = userService.namesOf(rows.stream().map(ConflictRow::getUserId).filter(Objects::nonNull).distinct().toList());
        Map<String, FieldError> errors = new LinkedHashMap<>();
        for (ConflictRow row : rows) {
            String range = texts.range(row.getStartsAt(), row.getDurationMinutes());
            if (row.getSameCandidate()) {
                errors.putIfAbsent("c" + row.getInterviewId(), new FieldError("startsAt", "interview.conflict.candidate", List.of(range)));
            }
            if (row.getUserId() != null) {
                errors.putIfAbsent(row.getUserId() + "" + row.getInterviewId(),
                        new FieldError("interviewerIds", "interview.conflict.interviewer", List.of(names.getOrDefault(row.getUserId(), "—"), range)));
            }
        }
        throw new ConflictException("interview.conflict", new ArrayList<>(errors.values()));
    }

    private List<ConflictRow> conflicts(Interview interview, UUID exceptId) {
        if (interview.getStatus() != InterviewStatus.SCHEDULED || interview.getInterviewerIds().isEmpty()) {
            return List.of();
        }
        return repository.findConflicts(exceptId, interview.getCandidateId(), interview.getInterviewerIds(), interview.getStartsAt(), interview.endsAt());
    }

    private static void requireOpen(Interview interview) {
        if (!interview.getStatus().isOpen()) {
            throw new BusinessException("interview.status.final");
        }
    }

    private static void requireVersion(Interview interview, Long version) {
        if (version == null || version != interview.getVersion()) {
            throw new ConflictException("error.concurrent-update", List.of(new FieldError("version", "error.concurrent-update")));
        }
    }

    /** What the calendars see: time (or slots), duration, mode, place and link. */
    private static String scheduleKey(Interview i) {
        return i.getStatus() + "|" + i.getStartsAt() + "|" + i.getProposedSlots() + "|" + i.getDurationMinutes() + "|" + i.getMode() + "|" + i.getLocation() + "|"
                + i.getMeetingUrl();
    }

    private static Set<UUID> union(Set<UUID> a, Set<UUID> b) {
        Set<UUID> result = new LinkedHashSet<>(a);
        result.addAll(b);
        return result;
    }

    private static Set<UUID> difference(Set<UUID> a, Set<UUID> b) {
        Set<UUID> result = new LinkedHashSet<>(a);
        result.removeAll(b);
        return result;
    }

    // =====================================================================================
    // Responses and notices
    // =====================================================================================

    private void publish(Interview interview, ApplicableJob job, Change change, UUID actorId, boolean notifyCandidate, boolean scheduleChanged, Set<UUID> added,
            Set<UUID> removed, boolean anonymous) {
        String candidateName = anonymous ? null : candidateService.getContact(interview.getCandidateId()).fullName();
        events.publishEvent(new InterviewChangedEvent(mapper.toNotice(interview, job.title(), job.clientId(), job.recruiterId(), candidateName), change, actorId,
                notifyCandidate, scheduleChanged, Set.copyOf(added), Set.copyOf(removed), anonymous));
    }

    private List<InterviewResponse> responses(List<Interview> interviews) {
        if (interviews.isEmpty()) {
            return List.of();
        }
        Map<UUID, CandidateContact> candidates = candidateService.contactsOf(interviews.stream().map(Interview::getCandidateId).distinct().toList());
        Map<UUID, ApplicableJob> jobs = new HashMap<>();
        interviews.stream().map(Interview::getJobId).distinct().forEach(jobId -> jobService.findApplicable(jobId).ifPresent(job -> jobs.put(jobId, job)));
        Map<UUID, String> names = userService.namesOf(interviews.stream().flatMap(i -> i.getInterviewerIds().stream()).distinct().toList());
        return interviews.stream().map(interview -> {
            CandidateContact candidate = candidates.get(interview.getCandidateId());
            ApplicableJob job = jobs.get(interview.getJobId());
            return mapper.toResponse(interview, new InterviewMapper.Refs(
                    candidate == null ? "—" : candidate.fullName(),
                    candidate != null && candidate.hasWhatsapp(),
                    job == null ? "—" : job.title(),
                    job == null ? null : job.clientId(),
                    job == null || job.clientName() == null ? "—" : job.clientName(),
                    interview.getInterviewerIds().stream().map(id -> names.getOrDefault(id, "—")).toList()));
        }).toList();
    }

    private Interview find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("interview.not-found"));
    }

    private ApplicableJob job(UUID jobId) {
        return jobService.findApplicable(jobId).orElseThrow(() -> new NotFoundException("job.not-found"));
    }

    private static boolean isInterviewer(AuthenticatedUser viewer) {
        return viewer.hasRole(UserRole.INTERVIEWER.name());
    }

    private static String trimmedOrNull(String value) {
        String text = Texts.blankToNull(value);
        return text == null ? null : text.trim();
    }
}
