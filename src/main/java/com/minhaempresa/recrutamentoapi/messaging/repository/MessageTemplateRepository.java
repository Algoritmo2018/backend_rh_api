package com.minhaempresa.recrutamentoapi.messaging.repository;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {

    List<MessageTemplate> findByActiveTrueOrderByNameAsc();

    Optional<MessageTemplate> findByCode(String code);

    boolean existsByCode(String code);
}
