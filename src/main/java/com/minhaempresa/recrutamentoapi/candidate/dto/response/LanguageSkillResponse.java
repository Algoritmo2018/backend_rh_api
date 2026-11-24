package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import com.minhaempresa.recrutamentoapi.candidate.entity.LanguageLevel;

/** Frontend {@code LanguageSkill}. */
public record LanguageSkillResponse(String language, LanguageLevel level) {}
