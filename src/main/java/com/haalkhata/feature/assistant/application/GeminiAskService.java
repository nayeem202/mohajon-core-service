package com.haalkhata.feature.assistant.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Voice / text business Q&A assistant ("Mohajon AI").
 *
 * The device already computes the shop snapshot (due totals, today's sales,
 * low stock, top customers...) from its offline Hive data and sends it as
 * {@code stats}. This service hands the question + snapshot to Gemini and
 * returns a friendly Bangla/English answer.
 */
@Service
public class GeminiAskService {

    private static final int MAX_TEXT = 800;

    private final GeminiApi geminiApi;
    private final ObjectMapper mapper;

    public GeminiAskService(GeminiApi geminiApi, ObjectMapper mapper) {
        this.geminiApi = geminiApi;
        this.mapper = mapper;
    }

    /** Answer a spoken or typed business question to a text question. */
    public Map<String, Object> answer(CurrentUser user, String text, String statsJson) {
        if (text == null || text.isBlank()) {
            throw BusinessException.badRequest("Question is empty.");
        }
        if (text.length() > MAX_TEXT) {
            throw BusinessException.badRequest("Question is too long.");
        }
        var prompt = buildPrompt(text, statsJson);
        var result = geminiApi.generate(prompt);
        return Map.of(
                "answer", result.path("answer").asText(""),
                "transcript", text);
    }

    /** Transcribe an audio question, then answer it just like {@link #answer}. */
    public Map<String, Object> answerAudio(String mimeType, byte[] audio, String statsJson) {
        if (audio == null || audio.length == 0) {
            throw BusinessException.badRequest("Voice audio is empty.");
        }
        var prompt = buildPrompt(null, statsJson);
        var result = geminiApi.generateWithAudio(prompt, mimeType, audio);
        return Map.of(
                "answer", result.path("answer").asText(""),
                "transcript", result.path("transcript").asText(""));
    }

    private String buildPrompt(String question, String statsJson) {
        String stats = (statsJson == null || statsJson.isBlank()) ? "{}" : statsJson;
        String q = (question == null || question.isBlank())
                ? "(speech attached)" : question;
        return "You are 'Mohajon AI', the voice assistant inside the Bangla shop-keeping app "
                + "Mohajon (মহাজন). The shopkeeper asked you a question about their "
                + "business. Here is a snapshot of their shop data (JSON):\nSTATS:\n"
                + stats
                + "\n\nQUESTION: "
                + q
                + "\n\nRules:\n"
                + "- Answer in the same language as the question (Bangla "
                + "preferred for Bangla questions, English for English ones).\n"
                + "- Be warm, brief (2-4 short sentences) and natural, like a helpful "
                + "shop assistant talking to the owner.\n"
                + "- When asked who owes the most (baki/due), read the "
                + "customers_with_due list and name the actual debtors with real "
                + "amounts in taka, e.g. \"আপনার সবচেয়ে বেশি বাকি রয়েছে রহিমের কাছে, "
                + "৫৪,০০০ টাকা। তারপর করিম ২১,০০০ এবং সালাম ৯,০০০ টাকা।\" Never say "
                + "\"আমি জানি না\" when customers_with_due already has names.\n"
                + "- For totals use real numbers from STATS (total_due_taka, "
                + "today_sales_taka, low_stock_count, customers_with_due).\n"
                + "- If the question cannot be answered from the given stats, say "
                + "you don't have that data yet.\n"
                + "- Only return valid JSON with exactly these fields: "
                + "{\"answer\":\"your reply\",\"transcript\":\"the user's question\"}.\n";
    }
}