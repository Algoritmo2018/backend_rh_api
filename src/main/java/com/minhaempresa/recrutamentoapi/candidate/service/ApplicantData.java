package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;

/**
 * Who applied (applications module, stage 8). Used only to create a new candidate: an existing
 * candidate with the same email is never changed by an application.
 *
 * @param phone already validated (any accepted format)
 * @param quickProfile fills a new profile (optional)
 */
public record ApplicantData(String fullName, String email, String phone, Province province, CandidateSource source, ApplicantQuickProfile quickProfile) {

    public ApplicantData(String fullName, String email, String phone, Province province, CandidateSource source) {
        this(fullName, email, phone, province, source, null);
    }

    @Override
    public String toString() {
        return "ApplicantData(source=" + source + ")";
    }
}
