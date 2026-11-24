package com.minhaempresa.recrutamentoapi.job.repository;

import com.minhaempresa.recrutamentoapi.job.entity.Job;
import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job>, JobRepositoryCustom {

    /**
     * Condition of a job visible on the public portal: open, and the deadline (if any) not passed.
     * Repeated in {@link JobRepositoryCustomImpl} (SQL) and {@link JobSpecifications}.
     */
    String PUBLICLY_VISIBLE = "j.status = com.minhaempresa.recrutamentoapi.job.entity.JobStatus.OPEN AND (j.deadline IS NULL OR j.deadline >= :today)";

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    @Query("SELECT j FROM Job j WHERE j.slug = :slug AND " + PUBLICLY_VISIBLE)
    Optional<Job> findPubliclyVisibleBySlug(@Param("slug") String slug, @Param("today") LocalDate today);

    /** Featured visible jobs, newest publication first (limit through the pageable). */
    @Query("SELECT j FROM Job j WHERE j.featured = TRUE AND " + PUBLICLY_VISIBLE + " ORDER BY j.publishedAt DESC, j.id DESC")
    List<Job> findFeatured(@Param("today") LocalDate today, Pageable limit);

    /** Visible jobs, newest publication first (limit through the pageable). */
    @Query("SELECT j FROM Job j WHERE " + PUBLICLY_VISIBLE + " ORDER BY j.publishedAt DESC, j.id DESC")
    List<Job> findVisible(@Param("today") LocalDate today, Pageable limit);

    @Query("SELECT j.area AS area, COUNT(j) AS total FROM Job j WHERE " + PUBLICLY_VISIBLE + " GROUP BY j.area")
    List<AreaCount> countVisibleByArea(@Param("today") LocalDate today);

    @Query("""
            SELECT j.clientId AS clientId,
                   COUNT(j) AS total,
                   SUM(CASE WHEN j.status = com.minhaempresa.recrutamentoapi.job.entity.JobStatus.OPEN THEN 1 ELSE 0 END) AS open
            FROM Job j WHERE j.clientId IN :clientIds GROUP BY j.clientId""")
    List<ClientJobCount> countByClient(@Param("clientIds") Collection<UUID> clientIds);

    /** Jobs per tag (link table {@code job_tags}). */
    @Query(value = "SELECT tag_id AS tagId, COUNT(*) AS total FROM job_tags GROUP BY tag_id", nativeQuery = true)
    List<TagCount> countByTag();

    interface AreaCount {
        JobArea getArea();

        long getTotal();
    }

    interface ClientJobCount {
        UUID getClientId();

        long getTotal();

        long getOpen();
    }

    interface TagCount {
        UUID getTagId();

        long getTotal();
    }
}
