package com.minhaempresa.recrutamentoapi.messaging.repository;

import com.minhaempresa.recrutamentoapi.messaging.entity.Message;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID>, JpaSpecificationExecutor<Message> {

    // ---- Sending limits (automatic sends do not count) ----

    long countBySentByAndAutomaticFalseAndCreatedAtAfter(UUID sentBy, Instant after);

    Optional<Message> findFirstBySentByAndAutomaticFalseAndCreatedAtAfterOrderByCreatedAtAsc(UUID sentBy, Instant after);

    long countByCandidateIdAndAutomaticFalseAndCreatedAtAfter(UUID candidateId, Instant after);

    Optional<Message> findFirstByCandidateIdAndAutomaticFalseAndCreatedAtAfterOrderByCreatedAtAsc(UUID candidateId, Instant after);

    // ---- Conversations (one per candidate, the most recent first) ----

    @Query(value = """
            SELECT candidate_id AS candidateId, count(*) AS total
            FROM messages
            GROUP BY candidate_id
            ORDER BY max(created_at) DESC, candidate_id""",
            countQuery = "SELECT count(DISTINCT candidate_id) FROM messages",
            nativeQuery = true)
    Page<ConversationRow> findConversations(Pageable pageable);

    interface ConversationRow {
        UUID getCandidateId();

        long getTotal();
    }

    /** The newest message of each candidate. */
    @Query(value = """
            SELECT DISTINCT ON (candidate_id) * FROM messages
            WHERE candidate_id IN (:candidateIds)
            ORDER BY candidate_id, created_at DESC, id DESC""", nativeQuery = true)
    List<Message> findLatestOf(@Param("candidateIds") Collection<UUID> candidateIds);

    // ---- Erasure ----

    @Query("SELECT m.id FROM Message m WHERE m.candidateId = :candidateId")
    List<UUID> findIdsByCandidateId(@Param("candidateId") UUID candidateId);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.candidateId = :candidateId")
    int deleteByCandidateId(@Param("candidateId") UUID candidateId);
}
