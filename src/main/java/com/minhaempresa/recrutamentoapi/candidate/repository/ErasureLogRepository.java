package com.minhaempresa.recrutamentoapi.candidate.repository;

import com.minhaempresa.recrutamentoapi.candidate.entity.ErasureLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErasureLogRepository extends JpaRepository<ErasureLog, UUID> {}
