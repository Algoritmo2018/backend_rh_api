package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import java.util.List;

/**
 * Candidate page of the internal panel (frontend {@code CandidateProfile}).
 *
 * @param candidate full for administrators and recruiters; without private data for interviewers
 * @param cv current CV document: always {@code null} until the documents arrive (stage 6b)
 * @param applications applications the viewer may see: always empty until stage 8
 * @param restricted interviewer view: no messages, documents (except the CV) nor references
 */
public record CandidateProfileResponse(CandidateResponse candidate, Object cv, List<Object> applications, boolean restricted) {}
