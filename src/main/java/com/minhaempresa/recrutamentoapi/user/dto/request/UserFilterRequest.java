package com.minhaempresa.recrutamentoapi.user.dto.request;

import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.List;
import java.util.UUID;

/**
 * Query parameters of {@code GET /users}: {@code ?roles=recruiter&roles=interviewer&clientId=…}.
 * Without {@code roles}, every role except candidates is listed (candidates manage their own account).
 */
public record UserFilterRequest(List<UserRole> roles, UUID clientId) {}
