package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.dto.request.ScreeningAnswerRequest;
import com.minhaempresa.recrutamentoapi.application.entity.ApplicationAnswer;
import com.minhaempresa.recrutamentoapi.job.dto.response.ScreeningQuestionResponse;
import com.minhaempresa.recrutamentoapi.job.entity.ScreeningQuestionType;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Checks the answers to a job's screening questions and decides the elimination (frontend {@code applySchema.ts}
 * and {@code applicationsService.apply}).
 *
 * <ul>
 *   <li>Every answer must be about a question of the job (once); a required question needs an answer.
 *   <li>Yes/no: {@code sim} or {@code nao}. Single choice: one of the options. Multiple choice: options joined
 *       by {@code |}, each one of the options, without repetitions. Text: free (at most 1000 characters).
 *   <li>Eliminated: an eliminatory question (yes/no or single choice) not answered with its expected answer
 *       (a missing answer too). The candidate never learns it.
 * </ul>
 *
 * Errors are on {@code screeningAnswers.<questionId>}.
 */
final class ScreeningAnswers {

    private static final Set<String> YES_NO = Set.of("sim", "nao");

    /** Answers to store (job order) and the elimination. */
    record Result(List<ApplicationAnswer> answers, boolean eliminated) {}

    private ScreeningAnswers() {}

    static Result check(List<ScreeningQuestionResponse> questions, List<ScreeningAnswerRequest> sent) {
        Map<UUID, String> byQuestion = new HashMap<>();
        List<FieldError> errors = new ArrayList<>();
        Set<UUID> known = new LinkedHashSet<>(questions.stream().map(ScreeningQuestionResponse::id).toList());
        for (ScreeningAnswerRequest answer : sent == null ? List.<ScreeningAnswerRequest>of() : sent) {
            if (!known.contains(answer.questionId()) || byQuestion.containsKey(answer.questionId())) {
                errors.add(new FieldError("screeningAnswers", "application.answer.unknown-question"));
                continue;
            }
            byQuestion.put(answer.questionId(), Texts.blankToNull(answer.answer()) == null ? null : answer.answer().trim());
        }

        List<ApplicationAnswer> answers = new ArrayList<>();
        boolean eliminated = false;
        for (ScreeningQuestionResponse question : questions) {
            String field = "screeningAnswers." + question.id();
            String answer = byQuestion.get(question.id());
            if (answer == null) {
                if (question.required()) {
                    errors.add(new FieldError(field, "application.answer.required"));
                }
                eliminated |= question.eliminatory();
                continue;
            }
            String normalized = normalize(question.type(), answer);
            if (normalized == null) {
                errors.add(new FieldError(field, "application.answer.invalid"));
                continue;
            }
            if (question.type() != ScreeningQuestionType.TEXT && !validChoice(question, normalized)) {
                errors.add(new FieldError(field, "application.answer.invalid"));
                continue;
            }
            if (question.eliminatory() && !normalized.equals(question.expectedAnswer())) {
                eliminated = true;
            }
            answers.add(new ApplicationAnswer(question.id(), question.question(), normalized));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("error.validation", errors);
        }
        return new Result(answers, eliminated);
    }

    /** Yes/no in lower case without accents ({@code Não} → {@code nao}); choices trimmed. */
    private static String normalize(ScreeningQuestionType type, String answer) {
        return switch (type) {
            case YES_NO -> Texts.fold(answer);
            case MULTIPLE_CHOICE -> {
                List<String> parts = Arrays.stream(answer.split("\\|")).map(String::trim).toList();
                yield parts.stream().anyMatch(String::isEmpty) || new LinkedHashSet<>(parts).size() != parts.size() ? null : String.join("|", parts);
            }
            default -> answer;
        };
    }

    private static boolean validChoice(ScreeningQuestionResponse question, String answer) {
        return switch (question.type()) {
            case YES_NO -> YES_NO.contains(answer);
            case SINGLE_CHOICE -> question.options().contains(answer);
            case MULTIPLE_CHOICE -> question.options().containsAll(List.of(answer.split("\\|")));
            case TEXT -> true;
        };
    }
}
