package com.minhaempresa.recrutamentoapi.notification.repository;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailRoute;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRouteRepository extends JpaRepository<EmailRoute, UUID> {

    Optional<EmailRoute> findByPurpose(EmailPurpose purpose);

    boolean existsByAccountId(UUID accountId);
}
