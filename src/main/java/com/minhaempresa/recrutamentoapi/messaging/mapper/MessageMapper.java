package com.minhaempresa.recrutamentoapi.messaging.mapper;

import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageResponse;
import com.minhaempresa.recrutamentoapi.messaging.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface MessageMapper {

    @Mapping(target = "sentById", source = "sentBy")
    MessageResponse toResponse(Message message);
}
