package com.minhaempresa.recrutamentoapi.application.dto.response;

import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.util.UUID;

/**
 * {@code candidate} of the frontend {@code PipelineCard}. Name, email, phone and province are the copy sent with
 * the application (D8-1); the rest comes from the profile. Interviewers get no email, phone nor photo (D6-1).
 */
public record PipelineCandidateResponse(
        UUID id,
        String fullName,
        String avatarUrl,
        Province province,
        String city,
        String headline,
        int totalExperienceYears,
        String email,
        String phone) {}
