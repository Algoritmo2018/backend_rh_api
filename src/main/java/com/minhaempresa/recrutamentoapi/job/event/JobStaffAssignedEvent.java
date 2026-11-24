package com.minhaempresa.recrutamentoapi.job.event;

import java.util.List;
import java.util.UUID;

/**
 * Staff newly assigned to a job (responsible recruiter or interviewer), published after a job is
 * created or its responsible staff change. The user who made the change is never included.
 */
public record JobStaffAssignedEvent(UUID jobId, String jobTitle, List<Assignee> assignees) {

    public JobStaffAssignedEvent {
        assignees = List.copyOf(assignees);
    }

    /**
     * @param asRecruiter assigned as the responsible recruiter (otherwise as interviewer)
     * @param canOpenJob administrator or recruiter: can open the job pipeline in the internal panel
     */
    public record Assignee(UUID userId, boolean asRecruiter, boolean canOpenJob) {}
}
