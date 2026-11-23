package com.minhaempresa.recrutamentoapi.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Builds the body of {@code PUT /settings/stages} from the current stages (tests restore them afterwards). */
public final class StageRequests {

    private StageRequests() {}

    public static List<Map<String, Object>> toUpdateRequests(JsonNode stages) {
        List<Map<String, Object>> requests = new ArrayList<>();
        for (JsonNode stage : stages) {
            Map<String, Object> request = new HashMap<>();
            request.put("id", stage.path("id").asString());
            request.put("name", stage.path("name").asString());
            request.put("order", stage.path("order").asInt());
            Map<String, Object> action = new HashMap<>();
            action.put("type", stage.path("autoAction").path("type").asString());
            JsonNode templateId = stage.path("autoAction").path("templateId");
            if (!templateId.isMissingNode() && !templateId.isNull()) {
                action.put("templateId", templateId.asString());
            }
            request.put("autoAction", action);
            requests.add(request);
        }
        return requests;
    }
}
