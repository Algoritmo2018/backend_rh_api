package com.minhaempresa.recrutamentoapi.application.listener;

import com.minhaempresa.recrutamentoapi.application.event.ApplicationSubmittedEvent;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageService;
import com.minhaempresa.recrutamentoapi.settings.dto.response.PipelineStageResponse;
import com.minhaempresa.recrutamentoapi.settings.dto.response.StageAutoActionResponse;
import com.minhaempresa.recrutamentoapi.settings.entity.StageAutoActionType;
import com.minhaempresa.recrutamentoapi.settings.service.PipelineStageService;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Automatic action of the "Nova" stage (by default the email "Candidatura recebida") for a new application.
 * After the commit and in its own transaction: a problem with the message (template deactivated, a variable
 * without value…) never undoes the application; it is only logged. The send is {@code automatic} (no sending
 * limits) and has no sender (no staff user caused it): a failed email is reported to the administrators.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationAutoActionListener {

    private final PipelineStageService stages;
    private final MessageService messageService;
    private final PlatformTransactionManager transactionManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(ApplicationSubmittedEvent event) {
        try {
            stages.list().stream()
                    .filter(stage -> stage.key() == PipelineStageKey.NEW)
                    .map(PipelineStageResponse::autoAction)
                    .filter(action -> action != null && action.type() == StageAutoActionType.SEND_MESSAGE && action.templateId() != null)
                    .findFirst()
                    .ifPresent(action -> send(event, action));
        } catch (RuntimeException e) {
            log.warn("Automatic action of the new stage failed for application {}: {}", event.applicationId(), e.getMessage());
        }
    }

    private void send(ApplicationSubmittedEvent event, StageAutoActionResponse action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> messageService.sendFromTemplate(
                action.templateId(), event.candidateId(), event.applicationId(), action.channel(), Map.of(), null, true));
    }
}
