package com.minhaempresa.recrutamentoapi.shared.persistence;

import com.minhaempresa.recrutamentoapi.shared.domain.Money;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Test-only entity (table created by db/test-migration) exercising the shared base classes. */
@Getter
@Setter
@Entity
@Table(name = "test_samples")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TestSample extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "province", nullable = false, length = 30)
    private Province province;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price_amount", precision = 19, scale = 2))
    @AttributeOverride(name = "currency", column = @Column(name = "price_currency", length = 3))
    private Money price;

    TestSample(String name, Province province, Money price) {
        this.name = name;
        this.province = province;
        this.price = price;
    }
}
