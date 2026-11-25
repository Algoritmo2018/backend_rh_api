package com.minhaempresa.recrutamentoapi.evaluation.mapper;

import com.minhaempresa.recrutamentoapi.evaluation.dto.response.EvaluationResponse;
import com.minhaempresa.recrutamentoapi.evaluation.entity.CompetencyScore;
import com.minhaempresa.recrutamentoapi.evaluation.entity.Evaluation;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface EvaluationMapper {

    /** Names and references of frontend {@code EvaluationWithRefs}. */
    record Refs(String candidateName, String jobTitle, String evaluatorName, EvaluationResponse.InterviewRef interview,
            List<EvaluationResponse.Reopening> reopenings) {}

    @Mapping(target = "id", source = "evaluation.id")
    @Mapping(target = "createdAt", source = "evaluation.createdAt")
    @Mapping(target = "candidateName", source = "refs.candidateName")
    @Mapping(target = "jobTitle", source = "refs.jobTitle")
    @Mapping(target = "evaluatorName", source = "refs.evaluatorName")
    @Mapping(target = "interview", source = "refs.interview")
    @Mapping(target = "reopenings", source = "refs.reopenings")
    EvaluationResponse toResponse(Evaluation evaluation, Refs refs);

    EvaluationResponse.Score toScore(CompetencyScore score);
}
