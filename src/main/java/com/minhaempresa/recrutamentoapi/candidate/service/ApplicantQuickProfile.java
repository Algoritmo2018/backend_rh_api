package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import java.util.List;

/**
 * Quick profile sent with an application instead of a CV ("não tenho CV"). It fills only a profile created by
 * that application (headline, skills, driver licenses) and counts in the match score of the application.
 */
public record ApplicantQuickProfile(
        String headline,
        List<String> skills,
        int years,
        EducationLevel educationLevel,
        String lastRole,
        String lastCompany,
        List<DriverLicenseCategory> driverLicenses) {}
