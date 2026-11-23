package com.minhaempresa.recrutamentoapi.offer.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Offer letter template: plain text with {@code {{variables}}}, never HTML. At most one is the default. */
@Getter
@Setter
@Entity
@Table(name = "offer_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferTemplate extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @Column(name = "code", nullable = false, length = 60, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "body", nullable = false, length = 8000)
    private String body;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    public OfferTemplate(String code, String name, String body, boolean isDefault) {
        this.code = code;
        this.name = name;
        this.body = body;
        this.isDefault = isDefault;
    }
}
