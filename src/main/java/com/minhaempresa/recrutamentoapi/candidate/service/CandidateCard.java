package com.minhaempresa.recrutamentoapi.candidate.service;

import java.util.UUID;

/**
 * What the pipeline cards show of a profile (the name and contacts come from the application, D8-1).
 *
 * @param avatarUrl inline path of the photo, or {@code null}
 */
public record CandidateCard(UUID id, String avatarUrl, String city, String headline, int totalExperienceYears) {}
