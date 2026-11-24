package com.minhaempresa.recrutamentoapi.client.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Message sent through a public form ("Precisa de recrutar?" or Contacto). Personal data kept only to
 * answer it (Lei n.º 22/11): deleted 12 months after being closed. The sender's IP is never stored.
 */
@Getter
@Setter
@Entity
@Table(name = "contact_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactRequest extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10, updatable = false)
    private ContactRequestKind kind;

    @Setter(AccessLevel.NONE)
    @Column(name = "name", nullable = false, length = 120, updatable = false)
    private String name;

    /** Company name (company requests only). */
    @Setter(AccessLevel.NONE)
    @Column(name = "company", length = 120, updatable = false)
    private String company;

    @Setter(AccessLevel.NONE)
    @Column(name = "email", nullable = false, length = 160, updatable = false)
    private String email;

    /** E.164. */
    @Setter(AccessLevel.NONE)
    @Column(name = "phone", nullable = false, length = 16, updatable = false)
    private String phone;

    @Setter(AccessLevel.NONE)
    @Column(name = "message", nullable = false, length = 2000, updatable = false)
    private String message;

    /** Number of people to recruit (company requests only). */
    @Setter(AccessLevel.NONE)
    @Column(name = "vacancies", updatable = false)
    private Integer vacancies;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ContactRequestStatus status = ContactRequestStatus.NEW;

    /** When it was closed: start of the retention period. */
    @Setter(AccessLevel.NONE)
    @Column(name = "closed_at")
    private Instant closedAt;

    public ContactRequest(ContactRequestKind kind, String name, String company, String email, String phone, String message, Integer vacancies) {
        this.kind = kind;
        this.name = name;
        this.company = company;
        this.email = email;
        this.phone = phone;
        this.message = message;
        this.vacancies = vacancies;
    }

    public void changeStatus(ContactRequestStatus newStatus, Instant now) {
        status = newStatus;
        closedAt = newStatus == ContactRequestStatus.CLOSED ? now : null;
    }
}
