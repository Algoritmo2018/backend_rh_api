package com.minhaempresa.recrutamentoapi.settings.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Label for candidates, applications and jobs. Deleting a tag really deletes it: the link tables
 * (created by those modules) reference it with {@code ON DELETE CASCADE}.
 */
@Getter
@Setter
@Entity
@Table(name = "tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends UserAuditableEntity {

    /** Unique, case-insensitive. */
    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "color", nullable = false, length = 20)
    private TagColor color;

    public Tag(String name, TagColor color) {
        this.name = name;
        this.color = color;
    }
}
