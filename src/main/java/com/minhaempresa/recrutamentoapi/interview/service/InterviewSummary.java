package com.minhaempresa.recrutamentoapi.interview.service;

import com.minhaempresa.recrutamentoapi.interview.entity.InterviewMode;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewType;
import java.time.Instant;
import java.util.UUID;

/** An interview as other modules show it (the evaluated interview of a scorecard, stage 9b). */
public record InterviewSummary(UUID id, InterviewType type, Instant startsAt, InterviewMode mode) {}
