package com.minhaempresa.recrutamentoapi.interview.mapper;

import com.minhaempresa.recrutamentoapi.interview.dto.response.InterviewResponse;
import com.minhaempresa.recrutamentoapi.interview.entity.Interview;
import com.minhaempresa.recrutamentoapi.interview.event.InterviewNotice;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface InterviewMapper {

    /** Names of the agenda (frontend {@code InterviewWithRefs}). */
    record Refs(String candidateName, boolean candidateHasWhatsapp, String jobTitle, UUID clientId, String clientName, List<String> interviewerNames) {}

    @Mapping(target = "id", source = "interview.id")
    @Mapping(target = "createdById", source = "interview.createdBy")
    @Mapping(target = "createdAt", source = "interview.createdAt")
    @Mapping(target = "updatedAt", source = "interview.updatedAt")
    @Mapping(target = "candidateName", source = "refs.candidateName")
    @Mapping(target = "candidateHasWhatsapp", source = "refs.candidateHasWhatsapp")
    @Mapping(target = "jobTitle", source = "refs.jobTitle")
    @Mapping(target = "clientId", source = "refs.clientId")
    @Mapping(target = "clientName", source = "refs.clientName")
    @Mapping(target = "interviewerNames", source = "refs.interviewerNames")
    InterviewResponse toResponse(Interview interview, Refs refs);

    /** What the notices need (immutable copy taken in the transaction of the change). */
    @Mapping(target = "interviewId", source = "interview.id")
    @Mapping(target = "jobTitle", source = "jobTitle")
    @Mapping(target = "clientId", source = "clientId")
    @Mapping(target = "recruiterId", source = "recruiterId")
    @Mapping(target = "candidateName", source = "candidateName")
    InterviewNotice toNotice(Interview interview, String jobTitle, UUID clientId, UUID recruiterId, String candidateName);
}
