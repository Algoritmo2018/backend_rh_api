package com.minhaempresa.recrutamentoapi.interview.listener;

import com.minhaempresa.recrutamentoapi.application.event.ApplicationsClosedEvent;
import com.minhaempresa.recrutamentoapi.interview.service.InterviewService;
import com.minhaempresa.recrutamentoapi.job.event.JobClosedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Automatic cancellation (D9-10), in the transaction of the change (synchronous listener): interviews still to happen
 * of rejected applications and of closed jobs. A paused job keeps its interviews.
 */
@Component
@RequiredArgsConstructor
public class InterviewCancellationListener {

    private final InterviewService interviewService;

    @EventListener
    public void onApplicationsClosed(ApplicationsClosedEvent event) {
        interviewService.cancelForApplications(event.applicationIds(), event.actorId());
    }

    @EventListener
    public void onJobClosed(JobClosedEvent event) {
        interviewService.cancelForJob(event.jobId(), event.actorId());
    }
}
