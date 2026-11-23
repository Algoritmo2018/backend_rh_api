package com.minhaempresa.recrutamentoapi.settings.repository;

import com.minhaempresa.recrutamentoapi.settings.entity.PipelineStage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, UUID> {

    List<PipelineStage> findAllByOrderByPositionAsc();

    /** Switches off the automatic actions that send a template (when it is deactivated). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PipelineStage s set s.autoActionTemplateId = null where s.autoActionTemplateId = :templateId")
    int clearAutoActionTemplate(UUID templateId);
}
