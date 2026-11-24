package com.minhaempresa.recrutamentoapi.notification.repository;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailAccountRepository extends JpaRepository<EmailAccount, UUID> {

    List<EmailAccount> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
