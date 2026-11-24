package com.minhaempresa.recrutamentoapi.notification.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SMTP account configured by an administrator. The password is only kept encrypted
 * ({@link #passwordEncrypted}, AES-256-GCM) and never leaves the backend.
 */
@Getter
@Setter
@Entity
@Table(name = "email_accounts")
@NoArgsConstructor
public class EmailAccount extends UserAuditableEntity {

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "host", nullable = false, length = 253)
    private String host;

    @Column(name = "port", nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(name = "security", nullable = false, length = 10)
    private EmailSecurity security;

    @Column(name = "username", length = 254)
    private String username;

    /** {@code v1:<base64>} (see {@code SecretCipher}); {@code null} = no password. */
    @Column(name = "password_encrypted", columnDefinition = "text")
    private String passwordEncrypted;

    @Column(name = "from_address", nullable = false, length = 254)
    private String fromAddress;

    @Column(name = "from_name", length = 80)
    private String fromName;

    /** Optional {@code Reply-To}. */
    @Column(name = "reply_to", length = 254)
    private String replyTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Override
    public String toString() {
        return "EmailAccount(id=" + getId() + ", host=" + host + ")";
    }
}
