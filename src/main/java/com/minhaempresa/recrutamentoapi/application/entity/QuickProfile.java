package com.minhaempresa.recrutamentoapi.application.entity;

import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Quick profile sent with an application instead of a CV ("não tenho CV"). Null when a CV was sent. */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuickProfile {

    @Column(name = "quick_headline", length = 80)
    private String headline;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "quick_skills", columnDefinition = "text[]")
    private List<String> skills = new ArrayList<>();

    @Column(name = "quick_years")
    private Integer years;

    @Enumerated(EnumType.STRING)
    @Column(name = "quick_education_level", length = 20)
    private EducationLevel educationLevel;

    @Column(name = "quick_last_role", length = 80)
    private String lastRole;

    @Column(name = "quick_last_company", length = 80)
    private String lastCompany;

    /** Category names ({@code A}…{@code E}). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "quick_driver_licenses", columnDefinition = "text[]")
    private List<String> driverLicenses = new ArrayList<>();

    public QuickProfile(String headline, List<String> skills, int years, EducationLevel educationLevel, String lastRole, String lastCompany,
            List<String> driverLicenses) {
        this.headline = headline;
        this.skills = new ArrayList<>(skills);
        this.years = years;
        this.educationLevel = educationLevel;
        this.lastRole = lastRole;
        this.lastCompany = lastCompany;
        this.driverLicenses = new ArrayList<>(driverLicenses);
    }
}
