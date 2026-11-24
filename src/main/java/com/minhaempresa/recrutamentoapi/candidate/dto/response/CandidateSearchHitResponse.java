package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.util.UUID;

/** Candidate in the global search of the top bar (frontend {@code SearchCandidateHit}). */
public record CandidateSearchHitResponse(UUID id, String fullName, String headline, Province province, String avatarUrl) {}
