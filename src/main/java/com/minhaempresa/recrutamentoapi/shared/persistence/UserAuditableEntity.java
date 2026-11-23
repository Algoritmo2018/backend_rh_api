package com.minhaempresa.recrutamentoapi.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * Editable record that also stores who created and last changed it ({@code created_by} /
 * {@code updated_by}, FK to {@code users} with {@code ON DELETE SET NULL}). Filled from the
 * authenticated user; {@code null} for system processes (seeds, bootstrap, scheduled jobs).
 */
@Getter
@MappedSuperclass
public abstract class UserAuditableEntity extends AuditableEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;
}
