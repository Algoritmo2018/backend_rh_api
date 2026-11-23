package com.minhaempresa.recrutamentoapi.settings.mapper;

import com.minhaempresa.recrutamentoapi.settings.dto.response.RejectionReasonResponse;
import com.minhaempresa.recrutamentoapi.settings.dto.response.TagResponse;
import com.minhaempresa.recrutamentoapi.settings.entity.RejectionReason;
import com.minhaempresa.recrutamentoapi.settings.entity.Tag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Rejection reasons and tags. Stages and company settings have derived fields and are mapped in their services. */
@Mapper
public interface CatalogMapper {

    @Mapping(target = "order", source = "position")
    RejectionReasonResponse toResponse(RejectionReason reason);

    TagResponse toResponse(Tag tag);
}
