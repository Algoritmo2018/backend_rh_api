package com.minhaempresa.recrutamentoapi.candidate.repository;

import com.minhaempresa.recrutamentoapi.candidate.entity.Candidate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidateRepository extends JpaRepository<Candidate, UUID>, CandidateRepositoryCustom {

    Optional<Candidate> findByUserId(UUID userId);

    /** {@code email} must already be lower case. */
    Optional<Candidate> findByEmail(String email);

    /** Every skill written in a profile, without repetitions (suggestions of the skill inputs). */
    @Query(value = "SELECT DISTINCT skill FROM candidates, unnest(skills) AS skill ORDER BY skill LIMIT 1000", nativeQuery = true)
    List<String> findDistinctSkills();

    /**
     * Quick search of the top bar: name, headline, email or phone contains the term (case and accents
     * ignored). {@code pattern} is already escaped and wrapped in {@code %}.
     */
    @Query(value = """
            SELECT id FROM candidates
            WHERE unaccent(lower(full_name || ' ' || headline || ' ' || email || ' ' || phone)) LIKE unaccent(lower(:pattern)) ESCAPE '\\'
            ORDER BY full_name, id LIMIT :limit""", nativeQuery = true)
    List<UUID> quickSearch(@Param("pattern") String pattern, @Param("limit") int limit);

    /** Name contains the term (case and accents ignored); {@code pattern} is already escaped and wrapped in {@code %}. */
    @Query(value = """
            SELECT id FROM candidates
            WHERE unaccent(lower(full_name)) LIKE unaccent(lower(:pattern)) ESCAPE '\\'
            ORDER BY full_name, id LIMIT :limit""", nativeQuery = true)
    List<UUID> findIdsByNameContaining(@Param("pattern") String pattern, @Param("limit") int limit);

    /** Candidates per tag (link table {@code candidate_tags}). */
    @Query(value = "SELECT tag_id AS tagId, COUNT(*) AS total FROM candidate_tags GROUP BY tag_id", nativeQuery = true)
    List<TagCount> countByTag();

    interface TagCount {
        UUID getTagId();

        long getTotal();
    }
}
