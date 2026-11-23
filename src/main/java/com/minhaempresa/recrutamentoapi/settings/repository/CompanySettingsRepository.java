package com.minhaempresa.recrutamentoapi.settings.repository;

import com.minhaempresa.recrutamentoapi.settings.entity.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Short> {}
