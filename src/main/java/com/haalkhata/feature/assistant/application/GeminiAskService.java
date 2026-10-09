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
    public Map<String, Object> answer(CurrentUser user, String text, String statsJson, String language) {
        if (text == null || text.isBlank()) {
            throw BusinessException.badRequest("Question is empty.");
        }
        if (text.length() > MAX_TEXT) {
            throw BusinessException.badRequest("Question is too long.");
        }
        var prompt = buildPrompt(text, statsJson, language);
        var result = geminiApi.generate(prompt);
        return Map.of(
                "answer", result.path("answer").asText(""),
                "transcript", text,
                "source", "Mohajon AI guidance");
    }

    /** Transcribe an audio question, then answer it just like {@link #answer}. */
    public Map<String, Object> answerAudio(CurrentUser user, String mimeType, byte[] audio, String statsJson, String language) {
        if (audio == null || audio.length == 0) {
            throw BusinessException.badRequest("Voice audio is empty.");
        }
        var prompt = buildPrompt(null, statsJson, language);
        var result = geminiApi.generateWithAudio(prompt, mimeType, audio);
        return Map.of(
                "answer", result.path("answer").asText(""),
                "transcript", result.path("transcript").asText(""),
                "source", "Mohajon AI guidance");
    }

    private String buildPrompt(String question, String statsJson, String language) {
        String stats = (statsJson == null || statsJson.isBlank()) ? "{}" : statsJson;
        String q = (question == null || question.isBlank())
                ? "(speech attached)" : question;
        String preferredLanguage = "bn".equalsIgnoreCase(language)
                ? "Bangla written in Bengali script" : "English";
        return "You are 'Mohajon AI', the voice assistant inside the Bangla shop-keeping app "
                + "Mohajon (মহাজন). The shopkeeper asked you a question about their "
                + "business. Here is a snapshot of their shop data (JSON):\nSTATS:\n"
                + stats
                + "\n\nQUESTION: "
                + q
                + "\n\nRules:\n"
                + "- The owner's selected response language is " + preferredLanguage
                + ". Use it even when the spoken or typed question is Banglish.\n"
                + "- You can help with sales, profit, pricing, stock, expiry and batches, "
                + "purchases, suppliers, expenses, cash, customer dues, repeat customers, "
                + "staff activity, returns, reports, barcode decisions and daily business "
                + "planning.\n"
                + "- Be warm, practical and natural, like a trusted shop assistant. Keep "
                + "the answer concise (normally 2-6 short sentences).\n"
                + "- STATS contains aggregate, privacy-minimised facts only. Never infer or invent "
                + "a customer's identity. If a question needs a named customer's record, say that "
                + "the app should look up the customer ledger.\n"
                + "- For totals use real numbers from STATS (total_due_taka, "
                + "today_sales_taka, today_gross_profit_taka, today_expenses_taka, "
                + "expenses_last_30d_taka, low_stock_count, customers_with_due, "
                + "top_selling_products_last_30d and inventory_snapshot).\n"
                + "- State the reporting period for any number. Treat every amount in STATS as a "
                + "recorded fact, not a guess. When a fact is unavailable, say so plainly.\n"
                + "- When the user asks for advice or a suggestion, always give at least "
                + "one safe, actionable next step based on the available data. Do not "
                + "invent a number, customer, product or transaction.\n"
                + "- If the exact answer needs data that is not in STATS, say what is "
                + "missing and still provide a useful general next step.\n"
                + "- Never claim to have completed an action (such as sending a message, "
                + "changing stock or making a payment) unless the app explicitly supports "
                + "that action in this request.\n"
                + "- Only return valid JSON with exactly these fields: "
                + "{\"answer\":\"your reply\",\"transcript\":\"the user's question\"}.\n";
    }
}
