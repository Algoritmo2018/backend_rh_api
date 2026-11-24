package com.minhaempresa.recrutamentoapi.candidate.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Implemented by the applications module (stage 8): numbers shown in the talent search results. */
public interface CandidateApplicationStatsContributor {

    /** Counts per candidate id (candidates without applications may be absent). */
    Map<UUID, ApplicationCounts> countsByCandidate(Collection<UUID> candidateIds);

    record ApplicationCounts(long total, long active) {}
}
