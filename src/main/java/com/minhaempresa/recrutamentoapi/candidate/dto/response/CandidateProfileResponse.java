package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import java.util.List;

/**
 * Candidate page of the internal panel (frontend {@code CandidateProfile}).
 *
 * @param candidate full for administrators and recruiters; without private data for interviewers
 * @param cv current CV document ({@code null} when none); interviewers get it too (decision D6-1)
 * @param applications frontend {@code InternalApplication[]} the viewer may see (interviewers: only the jobs
 *     assigned to them), the newest first
 * @param restricted interviewer view: no messages, documents (except the CV) nor references
 */
public record CandidateProfileResponse(CandidateResponse candidate, DocumentResponse cv, List<?> applications, boolean restricted) {}
