package com.minhaempresa.recrutamentoapi.evaluation.listener;

import com.minhaempresa.recrutamentoapi.evaluation.service.EvaluationService;
import com.minhaempresa.recrutamentoapi.interview.event.InterviewCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** An interview was marked {@code done}: pending evaluations for its interviewers, in the same transaction. */
@Component
@RequiredArgsConstructor
public class InterviewCompletedListener {

    private final EvaluationService evaluationService;

    @EventListener
    public void onCompleted(InterviewCompletedEvent event) {
        evaluationService.createPending(event);
    }
}
