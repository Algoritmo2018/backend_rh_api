package com.minhaempresa.recrutamentoapi.client.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Person to talk to at a client company. Exactly one contact of a client is the primary one. */
@Getter
@Setter
@Entity
@Table(name = "client_contacts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientContact extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "role", nullable = false, length = 80)
    private String role;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    /** E.164 ({@code +244923456789}). */
    @Column(name = "phone", nullable = false, length = 16)
    private String phone;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    ClientContact(Client client, String name, String role, String email, String phone) {
        this.client = client;
        this.name = name;
        this.role = role;
        this.email = email;
        this.phone = phone;
    }
}
