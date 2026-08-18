package com.haalkhata.feature.assistant.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haalkhata.core.exception.BusinessException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeminiCommandService {
    private static final List<String> ACTIONS = List.of(
            "open_home", "open_sales", "open_products", "open_customers",
            "open_more", "open_notifications", "open_investors", "open_expenses");

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${app.gemini-api-key:}")
    private String apiKey;

    @Value("${app.gemini-model:gemini-2.5-flash}")
    private String model;

    public GeminiCommandService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> interpret(String text) {
        if (text == null || text.isBlank()) {
            throw BusinessException.badRequest("Voice command is empty.");
        }
        if (text.length() > 500) {
            throw BusinessException.badRequest("Voice command is too long.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("AI_NOT_CONFIGURED", "AI voice assistant is not configured.");
        }
        try {
            var prompt = "Return JSON only. Understand this Bangla or English business app command. "
                    + "Allowed actions: " + ACTIONS + ". If it is not one of these, use unknown. "
                    + "Schema: {\\\"action\\\":\\\"open_products\\\",\\\"confidence\\\":0.0,"
                    + "\\\"reply\\\":\\\"short user-friendly reply\\\"}. Command: " + text;
            var requestBody = Map.of("contents", List.of(Map.of(
                    "parts", List.of(Map.of("text", prompt)))));
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("AI_UNAVAILABLE", "AI assistant is temporarily unavailable.");
            }
            JsonNode root = mapper.readTree(response.body());
            var raw = root.path("candidates").path(0).path("content").path("parts")
                    .path(0).path("text").asText().trim();
            JsonNode result = mapper.readTree(raw);
            var action = result.path("action").asText("unknown");
            if (!ACTIONS.contains(action)) action = "unknown";
            return Map.of(
                    "action", action,
                    "confidence", Math.max(0, Math.min(1, result.path("confidence").asDouble(0))),
                    "reply", result.path("reply").asText("Command processed."));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("AI_UNAVAILABLE", "AI assistant is temporarily unavailable.");
        }
    }
}
