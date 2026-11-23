package com.minhaempresa.recrutamentoapi.offer.repository;

import com.minhaempresa.recrutamentoapi.offer.entity.OfferTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OfferTemplateRepository extends JpaRepository<OfferTemplate, UUID> {

    List<OfferTemplate> findAllByOrderByNameAsc();

    boolean existsByCode(String code);

    /** Only one default: clears the flag on every template (before creating a new default). */
    @Modifying(flushAutomatically = true)
    @Query("update OfferTemplate t set t.isDefault = false where t.isDefault = true")
    int clearDefault();

    /** Only one default: clears the flag on every other template. */
    @Modifying(flushAutomatically = true)
    @Query("update OfferTemplate t set t.isDefault = false where t.isDefault = true and t.id <> :keepId")
    int clearDefaultExcept(UUID keepId);
}
