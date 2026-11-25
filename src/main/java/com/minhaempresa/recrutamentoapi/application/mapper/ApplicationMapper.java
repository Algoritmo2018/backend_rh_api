package com.minhaempresa.recrutamentoapi.application.mapper;

import com.minhaempresa.recrutamentoapi.application.dto.response.ApplicationResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.QuickProfileResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.ScreeningAnswerResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.StageMoveResponse;
import com.minhaempresa.recrutamentoapi.application.entity.Application;
import com.minhaempresa.recrutamentoapi.application.entity.ApplicationAnswer;
import com.minhaempresa.recrutamentoapi.application.entity.QuickProfile;
import com.minhaempresa.recrutamentoapi.application.entity.RequirementMatchEntry;
import com.minhaempresa.recrutamentoapi.application.entity.StageMove;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.MatchResultResponse.RequirementMatch;
import com.minhaempresa.recrutamentoapi.candidate.mapper.DocumentUrls;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(imports = {DocumentUrls.class, java.util.List.class})
public interface ApplicationMapper {

    @Mapping(target = "screeningAnswers", source = "answers")
    @Mapping(target = "sourceId", source = "source")
    @Mapping(target = "rejectedById", source = "rejectedBy")
    @Mapping(target = "clientComments", expression = "java(List.of())")
    @Mapping(target = "cvUrl", expression = "java(DocumentUrls.file(application.getCvDocumentId()))")
    ApplicationResponse toResponse(Application application);

    ScreeningAnswerResponse toAnswerResponse(ApplicationAnswer answer);

    RequirementMatch toMatchResponse(RequirementMatchEntry match);

    QuickProfileResponse toQuickProfileResponse(QuickProfile quickProfile);

    @Mapping(target = "id", source = "move.id")
    StageMoveResponse toResponse(StageMove move, String byUserName);
}
