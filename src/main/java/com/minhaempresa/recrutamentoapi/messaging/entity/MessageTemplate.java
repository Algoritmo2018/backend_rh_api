package com.minhaempresa.recrutamentoapi.messaging.entity;

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
 * Message template (email, SMS, WhatsApp). The body is plain text with {@code {{variables}}}; it is never
 * HTML. "Deleting" deactivates it, because sent messages keep a reference; system templates (used by
 * the code through their {@code code}) can never be deactivated.
 */
@Getter
@Setter
@Entity
@Table(name = "message_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTemplate extends UserAuditableEntity {

    /** Stable identifier used by the code (e.g. {@code rejection-email}); never changes. */
    @Setter(AccessLevel.NONE)
    @Column(name = "code", nullable = false, length = 60, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private MessageChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private MessageTemplateCategory category;

    @Column(name = "subject", length = 150)
    private String subject;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Setter(AccessLevel.NONE)
    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;

    public MessageTemplate(String code, String name, MessageChannel channel, MessageTemplateCategory category, String subject, String body) {
        this.code = code;
        this.name = name;
        this.channel = channel;
        this.category = category;
        this.subject = subject;
        this.body = body;
    }
}
