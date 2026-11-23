package com.minhaempresa.recrutamentoapi.messaging.mapper;

import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageTemplateResponse;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageTemplate;
import org.mapstruct.Mapper;

@Mapper
public interface MessageTemplateMapper {

    MessageTemplateResponse toResponse(MessageTemplate template);
}
