package com.haalkhata.feature.assistant.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haalkhata.core.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI provider for the Mohajon assistant.
 *
 * <p>Primary provider is Groq (free tier, ~14k requests/day) using an
 * OpenAI-compatible chat endpoint with the qwen model for strong Bangla and
 * Whisper for audio transcription. Gemini is used when Groq is not configured
 * or fails, so the assistant keeps working even if one provider is down.
 */
@Component
public class GeminiApi {

    private static final Logger log = LoggerFactory.getLogger(GeminiApi.class);

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .build();

    @Value("${app.gemini-api-key:}")
    private String geminiKey;

    @Value("${app.gemini-model:gemini-3.6-flash}")
    private String geminiModel;

    @Value("${app.ai-provider-mode:chain}")
    private String aiProviderMode;

    @Value("${app.orca-api-key:}")
    private String orcaKey;

    @Value("${app.orca-model:deepseek/deepseek-v4-flash-free}")
    private String orcaModel;

    @Value("${app.groq-api-key:}")
    private String groqKey;

    @Value("${app.groq-model:qwen/qwen3.8-27b}")
    private String groqModel;

    @Value("${app.groq-stt-model:whisper-large-v3-turbo}")
    private String groqSttModel;

    public GeminiApi(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode generate(String prompt) {
        if (geminiOnly()) {
            return generateWithGemini(prompt);
        }
        // Provider chain: Orca (free, good Bangla) → Gemini (best Bangla when still in quota) → Groq.
        BusinessException firstError = null;
        if (orcaConfigured()) {
            try {
                return openAiChat("https://api.orcarouter.ai/v1/chat/completions",
                        orcaKey, orcaModel, prompt);
            } catch (BusinessException ex) {
                log.warn("Orca failed ({}), trying next provider.", ex.getCode());
                firstError = ex;
            }
        }
        if (geminiConfigured()) {
            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("contents", List.of(Map.of(
                    "parts", List.of(Map.<String, Object>of("text", prompt)))));
            try {
                return geminiSend(requestBody);
            } catch (BusinessException ex) {
                log.warn("Gemini failed ({}), falling back to Groq.", ex.getCode());
                firstError = ex;
            }
        }
        if (groqConfigured()) {
            try {
                return openAiChat("https://api.groq.com/openai/v1/chat/completions",
                        groqKey, groqModel, prompt);
            } catch (BusinessException ex) {
                log.warn("Groq failed ({}), no provider left.", ex.getCode());
                firstError = ex;
            }
        }
        throw firstError != null
                ? firstError
                : new BusinessException("AI_NOT_CONFIGURED", "AI voice assistant is not configured.");
    }

    public JsonNode generateWithAudio(String prompt, String mimeType, byte[] audio) {
        if (geminiOnly()) {
            if (!geminiConfigured()) {
                throw new BusinessException("AI_NOT_CONFIGURED", "Gemini AI is not configured.");
            }
            return geminiSendWithAudio(prompt, mimeType, audio);
        }
        // Audio: the device's Whisper transcription is done below; providers only
        // see text, so route through the same text chain once we have a transcript.
        String resolved = null;
        if (geminiConfigured()) {
            try {
                return geminiSendWithAudio(prompt, mimeType, audio);
            } catch (BusinessException ex) {
                log.warn("Gemini audio failed ({}), using transcription fallback.", ex.getCode());
            }
        }
        // Whisper is stabilised for Bangla (language=bn, temperature=0, prompt bias).
        resolved = groqTranscribe(mimeType == null || mimeType.isBlank() ? "audio/wav" : mimeType, audio);
        String fullPrompt = prompt
                + "\n\nTRANSCRIBED_AUDIO_TEXT (transcribed by the speech engine):\n"
                + resolved;
        return generate(fullPrompt);
    }

    private boolean orcaConfigured() {
        return orcaKey != null && !orcaKey.isBlank();
    }

    private boolean geminiConfigured() {
        return geminiKey != null && !geminiKey.isBlank();
    }

    private boolean geminiOnly() {
        return "gemini-only".equalsIgnoreCase(aiProviderMode == null ? "" : aiProviderMode.trim());
    }

    private JsonNode generateWithGemini(String prompt) {
        if (!geminiConfigured()) {
            throw new BusinessException("AI_NOT_CONFIGURED", "Gemini AI is not configured.");
        }
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("contents", List.of(Map.of(
                "parts", List.of(Map.<String, Object>of("text", prompt)))));
        return geminiSend(requestBody);
    }

    private boolean groqConfigured() {
        return groqKey != null && !groqKey.isBlank();
    }

    private JsonNode geminiSendWithAudio(String prompt, String mimeType, byte[] audio) {
        if (audio == null || audio.length > 10_000_000) {
            throw new BusinessException("AUDIO_TOO_LARGE", "Audio file is too large.");
        }
        String audioB64 = Base64.getEncoder().encodeToString(audio);
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("contents", List.of(Map.of(
                "parts", List.of(
                        Map.<String, Object>of("text", prompt),
                        Map.of("inlineData", Map.of(
                                "mimeType", mimeType,
                                "data", audioB64))))));
        return geminiSend(requestBody);
    }

    /* ---------- OpenAI-compatible providers (Orca, Groq) ---------- */

    private JsonNode openAiChat(String uri, String key, String model, String prompt) {
        String body;
        try {
            body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.4,
                    "max_tokens", 1200,
                    "messages", List.of(
                            Map.of("role", "system",
                                    "content", "You are Mohajon AI, a helpful Bangla shop assistant. "
                                            + "Follow the user's instructions exactly and return only valid JSON."),
                            Map.of("role", "user", "content", prompt))));
        } catch (Exception e) {
            throw new BusinessException("AI_UNAVAILABLE", "Could not serialize AI request.");
        }
        String providerName = uri.contains("orcarouter") ? "Orca" : "Groq";
        BusinessException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(uri))
                        .timeout(java.time.Duration.ofSeconds(60))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + key)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    String detail = response.body() == null ? "" : response.body();
                    if (detail.length() > 500) detail = detail.substring(0, 500);
                    log.warn("{} returned HTTP {} on attempt {}: {}", providerName, status, attempt + 1, detail);
                    boolean retryable = status == 429 || status >= 500;
                    if (retryable && attempt < 2) {
                        Thread.sleep(1500L * (attempt + 1));
                        continue;
                    }
                    lastError = new BusinessException("AI_UNAVAILABLE",
                            "AI assistant is temporarily unavailable (" + status + ").");
                    continue;
                }
                JsonNode root = mapper.readTree(response.body());
                String raw = root.path("choices").path(0).path("message").path("content")
                        .asText("").trim();
                return parseResponse(raw);
            } catch (BusinessException exception) {
                lastError = exception;
                if (attempt < 2) {
                    try {
                        Thread.sleep(1500L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                break;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                lastError = new BusinessException("AI_UNAVAILABLE", "AI assistant request interrupted.");
                break;
            } catch (Exception exception) {
                log.warn("{} request failed on attempt {}: {}", providerName, attempt + 1, exception.toString());
                if (attempt < 2) {
                    try {
                        Thread.sleep(1500L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                lastError = new BusinessException("AI_UNAVAILABLE", "AI assistant is temporarily unavailable.");
                break;
            }
        }
        throw lastError != null
                ? lastError
                : new BusinessException("AI_UNAVAILABLE", "AI assistant is temporarily unavailable.");
    }

    private String groqTranscribe(String mimeType, byte[] audio) {
        if (audio == null || audio.length == 0) {
            throw new BusinessException("AUDIO_TOO_LARGE", "Audio file is empty.");
        }
        if (audio.length > 24_000_000) {
            throw new BusinessException("AUDIO_TOO_LARGE", "Audio file too large.");
        }
        String boundary = "----GroqBoundary" + System.nanoTime();
        var payload = new java.io.ByteArrayOutputStream();
        try {
            payload.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
                    + "Content-Type: " + mimeType + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            payload.write(audio);
            payload.write(("\r\n--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                    + groqSttModel + "\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"temperature\"\r\n\r\n"
                    + "0\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"language\"\r\n\r\n"
                    + "bn\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"prompt\"\r\n\r\n"
                    + "মহাজন দোকান আজ কত টাকার বিক্রি হলো কার কাছ থেকে সবচেয়ে বেশি বাকি পাবো "
                    + "কোন পণ্যের স্টক এখন কম আজ কত টাকা ক্যাশ পেয়েছি\r\n"
                    + "--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BusinessException("AI_UNAVAILABLE", "Could not build audio request.");
        }

        BusinessException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.groq.com/openai/v1/audio/transcriptions"))
                        .timeout(java.time.Duration.ofSeconds(60))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .header("Authorization", "Bearer " + groqKey)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray()))
                        .build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    String detail = response.body() == null ? "" : response.body();
                    if (detail.length() > 500) detail = detail.substring(0, 500);
                    log.warn("Groq transcription HTTP {} on attempt {}: {}", status, attempt + 1, detail);
                    boolean retryable = status == 429 || status >= 500;
                    if (retryable && attempt < 2) {
                        Thread.sleep(1500L * (attempt + 1));
                        continue;
                    }
                    lastError = new BusinessException("AI_UNAVAILABLE",
                            "Voice assistant is temporarily unavailable (" + status + ").");
                    continue;
                }
                JsonNode root = mapper.readTree(response.body());
                return root.path("text").asText("").trim();
            } catch (BusinessException exception) {
                lastError = exception;
                if (attempt < 2) {
                    try {
                        Thread.sleep(1500L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                break;
            } catch (Exception exception) {
                log.warn("Groq transcription failed on attempt {}: {}", attempt + 1, exception.toString());
                if (attempt < 2) {
                    try {
                        Thread.sleep(1500L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                lastError = new BusinessException("AI_UNAVAILABLE", "Voice assistant is temporarily unavailable.");
                break;
            }
        }
        throw lastError != null
                ? lastError
                : new BusinessException("AI_UNAVAILABLE", "Voice assistant is temporarily unavailable.");
    }

    /* ---------- Gemini fallback ---------- */

    private JsonNode geminiSend(Map<String, Object> requestBody) {
        if (geminiKey == null || geminiKey.isBlank()) {
            throw new BusinessException("AI_NOT_CONFIGURED", "AI voice assistant is not configured.");
        }
        String uri = "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModel + ":generateContent?key=" + geminiKey;
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        String body;
        try {
            body = mapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new BusinessException("AI_UNAVAILABLE", "Could not serialize AI request.");
        }

        BusinessException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(uri))
                        .timeout(java.time.Duration.ofSeconds(45))
                        .headers("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    String detail = response.body() == null ? "" : response.body();
                    if (detail.length() > 500) detail = detail.substring(0, 500);
                    log.warn("Gemini returned HTTP {} on attempt {}: {}", status, attempt + 1, detail);
                    boolean retryable = status == 429 || status >= 500;
                    if (status == 429) {
                        // Quota exhausted — fail fast instead of retrying Gemini.
                        lastError = new BusinessException("AI_UNAVAILABLE",
                                "AI assistant quota temporarily exceeded (" + status + ").");
                        break;
                    }
                    if (retryable && attempt < 2) {
                        Thread.sleep(1200L * (attempt + 1));
                        continue;
                    }
                    lastError = new BusinessException("AI_UNAVAILABLE",
                            "AI assistant is temporarily unavailable (" + status + ").");
                    continue;
                }
                JsonNode root = mapper.readTree(response.body());
                var raw = root.path("candidates").path(0).path("content").path("parts")
                        .path(0).path("text").asText().trim();
                return parseResponse(raw);
            } catch (BusinessException exception) {
                lastError = exception;
                if (attempt < 2) {
                    try {
                        Thread.sleep(1200L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                break;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                lastError = new BusinessException("AI_UNAVAILABLE", "AI assistant request interrupted.");
                break;
            } catch (Exception exception) {
                log.warn("Gemini request failed on attempt {}: {}", attempt + 1, exception.toString());
                if (attempt < 2) {
                    try {
                        Thread.sleep(1200L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                lastError = new BusinessException("AI_UNAVAILABLE", "AI assistant is temporarily unavailable.");
                break;
            }
        }
        throw lastError != null
                ? lastError
                : new BusinessException("AI_UNAVAILABLE", "AI assistant is temporarily unavailable.");
    }

    private JsonNode parseResponse(String raw) {
        raw = raw.trim();
        if (raw.startsWith("```")) {
            int first = raw.indexOf('\n');
            int last = raw.lastIndexOf("```");
            if (first > 0 && last > first) {
                raw = raw.substring(first + 1, last).trim();
            }
        }
        try {
            return mapper.readTree(raw);
        } catch (Exception first) {
            int braceStart = raw.indexOf('{');
            int braceEnd = raw.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                try {
                    return mapper.readTree(raw.substring(braceStart, braceEnd + 1));
                } catch (Exception ignored) {
                }
            }
            throw new BusinessException("AI_RESPONSE_INVALID", "AI returned an unreadable response.");
        }
    }
}
