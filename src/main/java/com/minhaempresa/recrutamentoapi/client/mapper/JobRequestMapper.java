package com.minhaempresa.recrutamentoapi.client.mapper;

import com.minhaempresa.recrutamentoapi.client.dto.response.JobRequestResponse;
import com.minhaempresa.recrutamentoapi.client.entity.JobRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface JobRequestMapper {

    @Mapping(target = "clientName", source = "clientName")
    @Mapping(target = "requestedByName", source = "requestedByName")
    JobRequestResponse toResponse(JobRequest request, String clientName, String requestedByName);
}
