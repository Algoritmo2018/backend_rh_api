package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import com.minhaempresa.recrutamentoapi.candidate.entity.LanguageLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Language of the profile (frontend {@code LanguageSkill}); the same language twice → 422. */
public record LanguageSkillRequest(
        @NotBlank(message = "{candidate.language.size}") @Size(min = 2, max = 30, message = "{candidate.language.size}") String language,
        @NotNull(message = "{validation.required}") LanguageLevel level) {}
