package com.minhaempresa.recrutamentoapi.user.dto.response;

import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.UUID;

/**
 * Internal staff member for pickers (job form, interview scheduling, agenda filters). No contact
 * details: the screens only use {@code id}, {@code name} and {@code role}.
 */
public record UserSummaryResponse(UUID id, String name, UserRole role, String avatarUrl, String jobTitle) {}
