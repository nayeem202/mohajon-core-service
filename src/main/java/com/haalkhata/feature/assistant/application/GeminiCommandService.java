package com.haalkhata.feature.assistant.application;

import com.haalkhata.core.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GeminiCommandService {
    private static final List<String> ACTIONS = List.of(
            "open_home", "open_sales", "open_products", "open_customers",
            "open_more", "open_notifications", "open_investors", "open_expenses");

    private final GeminiApi geminiApi;

    public GeminiCommandService(GeminiApi geminiApi) {
        this.geminiApi = geminiApi;
    }

    public Map<String, Object> interpret(String text) {
        if (text == null || text.isBlank()) {
            throw BusinessException.badRequest("Voice command is empty.");
        }
        if (text.length() > 500) {
            throw BusinessException.badRequest("Voice command is too long.");
        }
        var prompt = "Return JSON only. Understand this Bangla or English business app command. "
                + "Allowed actions: " + ACTIONS + ". If it is not one of these, use unknown. "
                + "Schema: {\\\"action\\\":\\\"open_products\\\",\\\"confidence\\\":0.0,"
                + "\\\"reply\\\":\\\"short user-friendly reply\\\"}. Command: " + text;
        var result = geminiApi.generate(prompt);
        var action = result.path("action").asText("unknown");
        if (!ACTIONS.contains(action)) action = "unknown";
        return Map.of(
                "action", action,
                "confidence", Math.max(0, Math.min(1, result.path("confidence").asDouble(0))),
                "reply", result.path("reply").asText("Command processed."));
    }
}