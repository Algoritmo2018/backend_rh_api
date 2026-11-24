package com.minhaempresa.recrutamentoapi.client.mapper;

import com.minhaempresa.recrutamentoapi.client.dto.response.ClientContactResponse;
import com.minhaempresa.recrutamentoapi.client.dto.response.ClientOptionResponse;
import com.minhaempresa.recrutamentoapi.client.dto.response.ClientResponse;
import com.minhaempresa.recrutamentoapi.client.entity.Client;
import com.minhaempresa.recrutamentoapi.client.entity.ClientContact;
import com.minhaempresa.recrutamentoapi.client.service.ClientStatsContributor.ClientCounts;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ClientMapper {

    @Mapping(target = "id", source = "client.id")
    @Mapping(target = "openJobs", source = "counts.openJobs")
    @Mapping(target = "totalJobs", source = "counts.totalJobs")
    @Mapping(target = "hires", source = "counts.hires")
    ClientResponse toResponse(Client client, ClientCounts counts);

    @Mapping(target = "isPrimary", source = "primary")
    ClientContactResponse toContactResponse(ClientContact contact);

    ClientOptionResponse toOption(Client client);
}
