package com.minhaempresa.recrutamentoapi.offer.mapper;

import com.minhaempresa.recrutamentoapi.offer.dto.response.OfferTemplateResponse;
import com.minhaempresa.recrutamentoapi.offer.entity.OfferTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OfferTemplateMapper {

    @Mapping(target = "isDefault", source = "default")
    OfferTemplateResponse toResponse(OfferTemplate template);
}
