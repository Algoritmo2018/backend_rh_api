package com.minhaempresa.recrutamentoapi.job.repository;

import com.minhaempresa.recrutamentoapi.job.dto.request.PublicJobSort;
import com.minhaempresa.recrutamentoapi.job.entity.ContractType;
import com.minhaempresa.recrutamentoapi.job.entity.ExperienceLevel;
import com.minhaempresa.recrutamentoapi.job.entity.Job;
import com.minhaempresa.recrutamentoapi.job.entity.WorkMode;
import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Queries that need PostgreSQL features (full-text search) or explicit locking. */
public interface JobRepositoryCustom {

    /** One page of ids of the publicly visible jobs matching the search, in the requested order. */
    PublicSearchPage searchPublic(PublicSearch search, long offset, int limit);

    /**
     * Increments the version now ({@code PESSIMISTIC_FORCE_INCREMENT}): an edit that only touches the
     * requirements or questions must still change the job version (and lock the row until commit).
     */
    void forceVersionIncrement(Job job);

    /**
     * Filters of the public search; {@code null} means "any".
     *
     * @param tsQuery {@code to_tsquery} expression built only from letters and digits ({@code null}: no
     *     text search)
     */
    record PublicSearch(
            LocalDate today,
            String tsQuery,
            Province province,
            JobArea area,
            ContractType contractType,
            WorkMode workMode,
            ExperienceLevel experienceLevel,
            Instant publishedSince,
            PublicJobSort sort) {}

    record PublicSearchPage(List<UUID> ids, long total) {}
}
