package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.dto.request.ReferenceCreateRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.request.ReferenceStatusRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.ReferenceResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.CandidateReference;
import com.minhaempresa.recrutamentoapi.candidate.mapper.CandidateMapper;
import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepository;
import com.minhaempresa.recrutamentoapi.candidate.repository.ReferenceRepository;
import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Professional references of candidates, registered and checked by administrators and recruiters. */
@Service
@RequiredArgsConstructor
public class ReferenceService {

    private final ReferenceRepository repository;
    private final CandidateRepository candidateRepository;
    private final CandidateMapper mapper;
    private final UserService userService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ReferenceResponse> listForCandidate(UUID candidateId) {
        requireCandidate(candidateId);
        return toResponses(repository.findByCandidateIdOrderByCreatedAtDescIdDesc(candidateId));
    }

    @Transactional
    public ReferenceResponse create(ReferenceCreateRequest request) {
        if (request.applicationId() != null) {
            throw new BusinessException("reference.application.unavailable", List.of(new FieldError("applicationId", "reference.application.unavailable")));
        }
        requireCandidate(request.candidateId());
        CandidateReference reference = new CandidateReference(request.candidateId());
        reference.setName(request.name().trim());
        reference.setCompany(request.company().trim());
        reference.setRole(request.role().trim());
        reference.setRelationship(request.relationship().trim());
        reference.setPhone(PhoneNumbers.normalizeOrThrow(request.phone()));
        String email = Texts.blankToNull(request.email());
        reference.setEmail(email == null ? null : email.toLowerCase(Locale.ROOT));
        repository.saveAndFlush(reference);
        return toResponses(List.of(reference)).getFirst();
    }

    /** Records the result of the check and who did it. */
    @Transactional
    public ReferenceResponse setStatus(UUID id, ReferenceStatusRequest request, AuthenticatedUser actor) {
        CandidateReference reference = repository.findById(id).orElseThrow(() -> new NotFoundException("reference.not-found"));
        reference.check(request.status(), Texts.blankToNull(request.notes()), actor.id(), clock.instant());
        repository.flush();
        return toResponses(List.of(reference)).getFirst();
    }

    private List<ReferenceResponse> toResponses(List<CandidateReference> references) {
        Map<UUID, String> names = userService.namesOf(references.stream().map(CandidateReference::getCheckedById).filter(Objects::nonNull).toList());
        return references.stream()
                .map(r -> mapper.toReferenceResponse(r, r.getCheckedById() == null ? null : names.get(r.getCheckedById())))
                .toList();
    }

    private void requireCandidate(UUID candidateId) {
        if (!candidateRepository.existsById(candidateId)) {
            throw new NotFoundException("candidate.not-found");
        }
    }
}
