package com.minhaempresa.recrutamentoapi.notification.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Which account sends the emails of a purpose (no route = the default server). */
@Getter
@Setter
@Entity
@Table(name = "email_routes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailRoute extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 15, updatable = false)
    private EmailPurpose purpose;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    public EmailRoute(EmailPurpose purpose, UUID accountId) {
        this.purpose = purpose;
        this.accountId = accountId;
    }
}
