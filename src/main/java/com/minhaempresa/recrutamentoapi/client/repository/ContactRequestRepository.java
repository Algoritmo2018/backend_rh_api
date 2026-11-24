package com.minhaempresa.recrutamentoapi.client.repository;

import com.minhaempresa.recrutamentoapi.client.entity.ContactRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, UUID>, JpaSpecificationExecutor<ContactRequest> {}
