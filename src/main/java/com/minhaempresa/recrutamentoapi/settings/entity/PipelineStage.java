package com.minhaempresa.recrutamentoapi.settings.entity;

import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configurable pipeline stage: name, position and automatic message. The key and the colour are fixed;
 * {@code new} is always first and {@code hired} always last.
 */
@Getter
@Setter
@Entity
@Table(name = "pipeline_stages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PipelineStage extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "stage_key", nullable = false, length = 30, updatable = false)
    private PipelineStageKey stageKey;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "position", nullable = false)
    private int position;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "color", nullable = false, length = 20, updatable = false)
    private StageColor color;

    /** Message template sent when an application enters the stage; {@code null} = no action. */
    @Column(name = "auto_action_template_id")
    private UUID autoActionTemplateId;
}
