package com.minhaempresa.recrutamentoapi.application.dto.response;

import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import java.util.List;

/** Quick profile sent with the application instead of a CV ("não tenho CV"). */
public record QuickProfileResponse(
        String headline,
        List<String> skills,
        Integer years,
        EducationLevel educationLevel,
        String lastRole,
        String lastCompany,
        List<String> driverLicenses) {}
