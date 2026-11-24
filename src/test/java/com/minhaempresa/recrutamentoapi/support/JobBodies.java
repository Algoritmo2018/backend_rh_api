package com.minhaempresa.recrutamentoapi.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Job form bodies as the frontend sends them ({@code JobInput}), for the job tests. */
public final class JobBodies {

    private JobBodies() {}

    /** A valid draft with one requirement of each kind used by the tests and two questions. */
    public static Map<String, Object> draft(UUID clientId, UUID recruiterId, UUID interviewerId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Técnico de HSE");
        body.put("clientId", clientId.toString());
        body.put("isConfidential", false);
        body.put("area", "hse");
        body.put("province", "Zaire");
        body.put("city", "Soyo");
        body.put("workMode", "onsite");
        body.put("contractType", "fixed_term");
        body.put("experienceLevel", "mid");
        body.put("minExperienceYears", 3);
        body.put("vacancies", 2);
        body.put("isExpatriate", false);
        body.put("isFeatured", false);
        body.put("description", "<p>Garantir o cumprimento das normas de <strong>segurança</strong> em estaleiro offshore.</p>");
        body.put("responsibilities", List.of(" Inspeções diárias ", "Relatórios de incidentes"));
        body.put("benefits", List.of("Seguro de saúde"));
        body.put("requirements", new ArrayList<>(List.of(
                requirement(null, "Curso NEBOSH", "mandatory", 5, "skill", "NEBOSH", null),
                requirement(null, "3 anos em HSE", "desirable", 3, "experience", "", 3))));
        body.put("screeningQuestions", new ArrayList<>(List.of(
                question(null, "Tem disponibilidade para regime de rotação?", "yes_no", List.of(), true, true, "sim"),
                question(null, "Qual o seu nível de inglês?", "single_choice", List.of("Básico", "Intermédio", "Avançado"), false, false, null))));
        body.put("salary", Map.of("min", 450000, "max", 650000, "currency", "AOA", "period", "month", "visible", true));
        body.put("status", "draft");
        body.put("recruiterId", recruiterId.toString());
        body.put("interviewerIds", List.of(interviewerId.toString()));
        body.put("testId", null);
        body.put("deadline", LocalDate.now().plusDays(30).toString());
        body.put("tagIds", List.of());
        return body;
    }

    public static Map<String, Object> requirement(String id, String description, String type, int weight, String category, String value, Integer minValue) {
        Map<String, Object> requirement = new HashMap<>();
        requirement.put("id", id);
        requirement.put("description", description);
        requirement.put("type", type);
        requirement.put("weight", weight);
        requirement.put("category", category);
        requirement.put("value", value);
        requirement.put("minValue", minValue);
        return requirement;
    }

    public static Map<String, Object> question(String id, String text, String type, List<String> options, boolean required, boolean eliminatory, String expectedAnswer) {
        Map<String, Object> question = new HashMap<>();
        question.put("id", id);
        question.put("question", text);
        question.put("type", type);
        question.put("options", options);
        question.put("required", required);
        question.put("eliminatory", eliminatory);
        question.put("expectedAnswer", expectedAnswer);
        return question;
    }
}
