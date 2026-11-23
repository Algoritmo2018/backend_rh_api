package com.minhaempresa.recrutamentoapi.shared.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TestSampleRepository extends JpaRepository<TestSample, UUID> {}
