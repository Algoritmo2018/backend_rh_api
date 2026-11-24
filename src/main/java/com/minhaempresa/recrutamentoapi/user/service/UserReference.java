package com.minhaempresa.recrutamentoapi.user.service;

import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.UUID;

/**
 * Minimal view of a user for other modules (responsible staff of a job, author names). Internal to
 * the backend: no contact details.
 */
public record UserReference(UUID id, String name, UserRole role, boolean active) {

    /** Active FEH OIRAM staff (administrator, recruiter or interviewer). */
    public boolean isActiveStaff() {
        return active && UserRole.INTERNAL.contains(role);
    }

    /** Active administrator or recruiter (can be responsible for a job and open the job pipeline). */
    public boolean isActiveRecruiter() {
        return active && (role == UserRole.ADMIN || role == UserRole.RECRUITER);
    }
}
