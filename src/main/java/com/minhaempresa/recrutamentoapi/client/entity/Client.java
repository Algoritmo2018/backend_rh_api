package com.minhaempresa.recrutamentoapi.client.entity;

import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Client company (FEH OIRAM recruits for it). Never deleted: deactivated. The company does not charge
 * fees to clients, so there is no billing data here.
 */
@Getter
@Setter
@Entity
@Table(name = "clients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client extends UserAuditableEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "sector", nullable = false, length = 20)
    private ClientSector sector;

    /** Angolan tax identification number (9 to 14 digits); unique among clients. */
    @Column(name = "nif", nullable = false, length = 14)
    private String nif;

    @Enumerated(EnumType.STRING)
    @Column(name = "province", nullable = false, length = 30)
    private Province province;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "website", length = 200)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ClientStatus status = ClientStatus.ACTIVE;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<ClientContact> contacts = new ArrayList<>();

    public Client(String name, ClientSector sector, String nif, Province province, String city, String address) {
        this.name = name;
        this.sector = sector;
        this.nif = nif;
        this.province = province;
        this.city = city;
        this.address = address;
    }

    public boolean isActive() {
        return status == ClientStatus.ACTIVE;
    }

    public ClientContact addContact(String name, String role, String email, String phone) {
        ClientContact contact = new ClientContact(this, name, role, email, phone);
        contacts.add(contact);
        return contact;
    }
}
