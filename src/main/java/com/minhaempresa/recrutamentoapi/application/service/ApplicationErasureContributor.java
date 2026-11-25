package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.entity.Application;
import com.minhaempresa.recrutamentoapi.application.entity.ApplicationStatus;
import com.minhaempresa.recrutamentoapi.application.entity.StageMove;
import com.minhaempresa.recrutamentoapi.application.entity.StageMoveAction;
import com.minhaempresa.recrutamentoapi.application.repository.ClientCommentRepository;
import com.minhaempresa.recrutamentoapi.application.repository.ApplicationRepository;
import com.minhaempresa.recrutamentoapi.application.repository.StageMoveRepository;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateErasureContributor;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasure of a candidate (D8-8): the applications stay (job statistics, "Candidato removido") without any
 * personal data: sent name/email/phone/province, CV link, quick profile, answers, requirement details, notes and
 * the reasons of the history and the shortlist comments are removed (the "CV para o cliente" goes with the
 * candidate's documents). Applications in progress end as withdrawn, so the client no longer sees them.
 */
@Component
@RequiredArgsConstructor
class ApplicationErasureContributor implements CandidateErasureContributor {

    private final ApplicationRepository repository;
    private final StageMoveRepository stageMoves;
    private final ClientCommentRepository clientComments;
    private final Messages messages;
    private final Clock clock;

    @Override
    @Transactional
    public void eraseCandidate(UUID candidateId) {
        List<Application> applications = repository.findByCandidateId(candidateId);
        if (applications.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        String removed = messages.get("candidate.removed-name");
        for (Application application : applications) {
            boolean active = application.getStatus() == ApplicationStatus.ACTIVE;
            application.anonymize(removed);
            if (active) {
                stageMoves.save(new StageMove(application.getId(), StageMoveAction.WITHDRAWN, application.getStage(), application.getStage(), null, null, now));
            }
        }
        repository.flush();
        List<UUID> ids = applications.stream().map(Application::getId).toList();
        stageMoves.clearReasons(ids);
        clientComments.deleteByApplicationIds(ids);
    }
}
