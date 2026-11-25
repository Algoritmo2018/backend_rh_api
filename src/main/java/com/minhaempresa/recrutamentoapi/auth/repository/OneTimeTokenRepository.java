package com.minhaempresa.recrutamentoapi.auth.repository;

import com.minhaempresa.recrutamentoapi.auth.entity.OneTimeToken;
import com.minhaempresa.recrutamentoapi.auth.entity.OneTimeTokenType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OneTimeToken> findByTypeAndTokenHash(OneTimeTokenType type, String tokenHash);

    /** Latest token of a type for a user (codes are looked up by user, not by hash). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OneTimeToken> findFirstByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, OneTimeTokenType type);

    /** A new token replaces the pending ones of the same type. */
    @Modifying(flushAutomatically = true)
    @Query("update OneTimeToken t set t.usedAt = :now where t.userId = :userId and t.type = :type and t.usedAt is null")
    int invalidatePending(UUID userId, OneTimeTokenType type, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("delete from OneTimeToken t where t.userId = :userId and t.type = :type")
    int deleteByUserAndType(UUID userId, OneTimeTokenType type);

    /** Daily cleanup: expired tokens are useless (a used token always expires too). */
    @Modifying
    @Query("delete from OneTimeToken t where t.expiresAt < :before")
    int deleteExpiredBefore(Instant before);
}
