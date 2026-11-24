package com.minhaempresa.recrutamentoapi.client.mapper;

import com.minhaempresa.recrutamentoapi.client.dto.response.ContactRequestResponse;
import com.minhaempresa.recrutamentoapi.client.entity.ContactRequest;
import org.mapstruct.Mapper;

@Mapper
public interface ContactRequestMapper {

    ContactRequestResponse toResponse(ContactRequest request);
}
