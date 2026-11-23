package com.minhaempresa.recrutamentoapi.settings.listener;

import com.minhaempresa.recrutamentoapi.messaging.event.MessageTemplateDeactivatedEvent;
import com.minhaempresa.recrutamentoapi.settings.service.PipelineStageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Switches off the automatic actions of a deactivated template. Runs before the commit, inside the same
 * transaction: the template is never inactive while a stage still points to it.
 */
@Component
@RequiredArgsConstructor
public class MessageTemplateDeactivatedListener {

    private final PipelineStageService pipelineStages;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDeactivated(MessageTemplateDeactivatedEvent event) {
        pipelineStages.clearAutoActionsUsing(event.templateId());
    }
}
