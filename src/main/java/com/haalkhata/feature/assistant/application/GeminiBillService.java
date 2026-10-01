package com.haalkhata.feature.assistant.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.product.domain.Product;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeminiBillService {

    private static final Logger log = LoggerFactory.getLogger(GeminiBillService.class);
    private static final int MAX_CATALOG_CHARS = 8000;
    private static final double FUZZY_THRESHOLD = 0.45;

    private final GeminiApi geminiApi;
    private final ProductRepository productRepository;

    public Map<String, Object> interpretBillAudio(CurrentUser user, String mimeType, byte[] audio) {
        if (audio == null || audio.length == 0) {
            throw BusinessException.badRequest("Voice audio is empty.");
        }
        List<Product> products = productRepository.findByShopIdAndIsDeletedFalseOrderByNameEnAsc(user.getShopId());
        if (products.isEmpty()) {
            return Map.of(
                    "items", List.of(),
                    "transcript", "",
                    "reply", "আপনার কোনো পণ্য এখনো ক্যাটালগে নেই।");
        }
        String catalog = buildCatalog(products);
        String prompt = "You are a bilingual (Bangla/English) shop billing assistant for a Bangladeshi shopkeeper. "
                + "The user speaks an order aloud in Bangla or English (often mixed). "
                + "FIRST transcribe the audio exactly as spoken. "
                + "THEN loosely match the spoken items to products in the catalog.\n\n"
                + "MATCHING RULES:\n"
                + "- Match by substring and phonetic similarity ('chocolate'='choklet', 'mango'='আম', 'dahi'='doi').\n"
                + "- Match Bangla speech transliterated to English.\n"
                + "- Match partial words ('choco' matches 'chocolate').\n"
                + "- Extract quantities; default 1 if unclear.\n"
                + "- Skip separator words ('ar', 'ebong', 'o', 'and', 'plus', 'প্লাস', 'ও', 'এবং').\n\n"
                + "OUTPUT JSON ONLY with schema: "
                + "{\"items\":[{\"productId\":\"EXACT_ID_FROM_CATALOG\",\"quantity\":N}],"
                + "\"transcript\":\"the exact transcribed speech\","
                + "\"reply\":\"short friendly reply in Bangla\"}\n"
                + "Never invent productId values. Only use IDs from the catalog.\n\n"
                + "CATALOG:\n" + catalog + "\n\n"
                + "USER AUDIO is embedded in the request.";

        JsonNode result = geminiApi.generateWithAudio(prompt, mimeType == null ? "audio/wav" : mimeType, audio);
        List<Map<String, Object>> items = extractValidItems(result, productMap(products));

        boolean needTranscriptFallback = result.path("transcript").asText("").isBlank();
        String transcript = result.path("transcript").asText("");
        String reply = result.path("reply").asText("");

        if (items.isEmpty()) {
            List<Map<String, Object>> fuzzy = fuzzyMatch(transcript.isBlank() ? "" : transcript, products);
            if (fuzzy.isEmpty() && needTranscriptFallback) {
                fuzzy = fuzzyMatch(tryAudioTranscriptFallback(user, mimeType, audio), products);
            }
            items = fuzzy;
            if (!fuzzy.isEmpty() && reply.isBlank()) {
                reply = "আইটেম পাওয়া গেছে।";
            }
        }

        if (items.isEmpty()) {
            reply = reply.isBlank() ? "কথা ভালোভাবে শোনা যায়নি। আবার বলুন।" : reply;
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("items", items);
        resultMap.put("transcript", transcript);
        resultMap.put("reply", reply);
        return resultMap;
    }

    public Map<String, Object> interpretBill(CurrentUser user, String text) {
        if (text == null || text.isBlank()) {
            throw BusinessException.badRequest("Voice bill is empty.");
        }
        if (text.length() > 500) {
            throw BusinessException.badRequest("Voice bill is too long.");
        }
        List<Product> products = productRepository.findByShopIdAndIsDeletedFalseOrderByNameEnAsc(user.getShopId());
        if (products.isEmpty()) {
            return Map.of(
                    "items", List.of(),
                    "reply", "আপনার কোনো পণ্য এখনো ক্যাটালগে নেই।");
        }

        String catalog = buildCatalog(products);
        String prompt = buildPrompt(catalog, text);

        JsonNode result;
        try {
            result = geminiApi.generate(prompt);
        } catch (Exception ex) {
            log.warn("Gemini call failed, falling back to fuzzy: {}", ex.getMessage());
            result = null;
        }

        Map<String, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));

        List<Map<String, Object>> items = new ArrayList<>();
        String reply = "";

        if (result != null) {
            items = extractValidItems(result, byId);
            reply = result.path("reply").asText("");
        }

        if (items.isEmpty()) {
            items = fuzzyMatch(text, products);
            if (!items.isEmpty()) {
                reply = "আইটেম পাওয়া গেছে।";
            }
        }

        if (items.isEmpty()) {
            reply = reply.isEmpty() ? "কিছু মেলানো যায়নি। আবার চেষ্টা করুন।" : reply;
        }

        return Map.of("items", items, "reply", reply);
    }

    private String buildCatalog(List<Product> products) {
        String catalog = products.stream()
                .map(p -> p.getProductId() + "|" + p.getNameEn() + "|"
                        + (p.getNameBn() == null ? "" : p.getNameBn())
                        + "|price=" + p.getSellingPrice() + "|stock=" + p.getStockQty()
                        + "|" + (p.getUnit() == null ? "pcs" : p.getUnit()))
                .collect(Collectors.joining("; "));
        if (catalog.length() > MAX_CATALOG_CHARS) {
            catalog = catalog.substring(0, MAX_CATALOG_CHARS);
        }
        return catalog;
    }

    private Map<String, Product> productMap(List<Product> products) {
        Map<String, Product> byId = new HashMap<>();
        for (Product p : products) {
            byId.put(p.getProductId(), p);
        }
        return byId;
    }

    private String tryAudioTranscriptFallback(CurrentUser user, String mimeType, byte[] audio) {
        try {
            List<Product> products = productRepository.findByShopIdAndIsDeletedFalseOrderByNameEnAsc(user.getShopId());
            String catalog = buildCatalog(products);
            String prompt = "Transcribe this audio. It is a Bangladeshi shopkeeper dictating a customer order "
                    + "in Bangla or mixed Bangla-English. Output JSON only: {\"transcript\":\"transcription\"}.\n"
                    + "CATALOG (for context only):\n" + catalog;
            JsonNode result = geminiApi.generateWithAudio(prompt, mimeType == null ? "audio/wav" : mimeType, audio);
            return result.path("transcript").asText("");
        } catch (Exception ex) {
            log.warn("Audio transcript fallback failed: {}", ex.getMessage());
            return "";
        }
    }

    private String buildPrompt(String catalog, String text) {
        return "You are a bilingual (Bangla/English) shop billing assistant for a Bangladeshi shopkeeper. "
                + "The user speaks items aloud using speech-to-text. The transcription may have errors: "
                + "misheard words, wrong spelling, phonetic typos, mixed Bangla-English, incomplete words. "
                + "You MUST match spoken items to catalog products very loosely.\n\n"
                + "MATCHING RULES:\n"
                + "- Match by substring: if spoken word is CONTAINED in a product name (or vice versa), it is a match.\n"
                + "- Match by phonetic similarity: 'frozen'='frozn', 'chocolate'='choklet', 'dahi'='doi'.\n"
                + "- Match Bangla speech transliterated to English: 'mango'='আম', 'rice'='চাল', 'milk'='দুধ'.\n"
                + "- Match partial words: 'choco' matches 'chocolate', 'bottl' matches 'bottle'.\n"
                + "- Common speech errors: 'doe'='dahi', 'paneer'='panir', 'biskut'='biscuit'.\n"
                + "- Quantity: extract numbers near item names. Default 1 if unclear.\n"
                + "- Separator words to ignore: 'ar', 'ebong', 'o', 'and', 'plus', 'with', 'তারপর', 'এবং', 'ও'.\n\n"
                + "OUTPUT: JSON only. Schema: {\"items\":[{\"productId\":\"EXACT_ID_FROM_CATALOG\",\"quantity\":N}],\"reply\":\"friendly reply\"}\n"
                + "NEVER invent a productId. Only use IDs from the catalog.\n\n"
                + "CATALOG:\n" + catalog + "\n\n"
                + "USER ORDER: " + text;
    }

    private List<Map<String, Object>> extractValidItems(JsonNode result, Map<String, Product> byId) {
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode node : result.path("items")) {
            String productId = node.path("productId").asText("").trim();
            Product product = byId.get(productId);
            if (product == null || !seen.add(productId)) {
                continue;
            }
            double quantity = node.path("quantity").isMissingNode() || node.path("quantity").isNull()
                    ? 1 : node.path("quantity").asDouble(1);
            if (quantity <= 0) quantity = 1;
            items.add(buildItemMap(product, quantity));
        }
        return items;
    }

    private List<Map<String, Object>> fuzzyMatch(String transcript, List<Product> products) {
        List<Map<String, Object>> results = new ArrayList<>();
        Set<String> used = new HashSet<>();
        String lower = transcript.toLowerCase(Locale.ROOT);
        String[] tokens = lower.split("[\\s,;]+");
        List<String> words = new ArrayList<>();
        for (String t : tokens) {
            String cleaned = t.replaceAll("[^a-zA-Z\u0980-\u09FF]", "");
            if (cleaned.length() >= 2) {
                words.add(cleaned);
            }
        }
        double[] quantities = extractQuantities(lower);

        for (Product p : products) {
            if (used.contains(p.getProductId())) continue;
            String nameEn = (p.getNameEn() == null ? "" : p.getNameEn()).toLowerCase(Locale.ROOT);
            String nameBn = (p.getNameBn() == null ? "" : p.getNameBn()).toLowerCase(Locale.ROOT);
            double bestScore = 0;
            for (String word : words) {
                if (word.length() < 2) continue;
                double score = Math.max(
                        similarityScore(word, nameEn),
                        similarityScore(word, nameBn));
                if (score >= FUZZY_THRESHOLD && score > bestScore) {
                    bestScore = score;
                }
            }
            if (bestScore >= FUZZY_THRESHOLD) {
                used.add(p.getProductId());
                double qty = quantities.length > 0 ? quantities[0] : 1;
                if (quantities.length > 0) {
                    quantities[0] = -1;
                }
                results.add(buildItemMap(p, qty));
            }
        }
        return results;
    }

    private double similarityScore(String input, String target) {
        if (target.isEmpty() || input.isEmpty()) return 0;
        if (target.contains(input) || input.contains(target)) return 1.0;
        int maxLen = Math.max(input.length(), target.length());
        int dist = levenshtein(input, target);
        double score = 1.0 - ((double) dist / maxLen);
        if (score < FUZZY_THRESHOLD) {
            int window = input.length();
            for (int i = 0; i <= target.length() - Math.min(window, target.length()); i++) {
                String sub = target.substring(i, Math.min(i + window + 2, target.length()));
                int subDist = levenshtein(input, sub);
                double subScore = 1.0 - ((double) subDist / Math.max(input.length(), sub.length()));
                if (subScore > score) {
                    score = subScore;
                }
            }
        }
        return score;
    }

    private int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        int[] prev = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            int[] curr = new int[n + 1];
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    curr[j] = prev[j - 1];
                } else {
                    curr[j] = 1 + Math.min(prev[j - 1], Math.min(prev[j], curr[j - 1]));
                }
            }
            prev = curr;
        }
        return prev[n];
    }

    private double[] extractQuantities(String text) {
        Pattern p = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(টি|পিস|pcs|kg|পয়েন্ট|liter|লিটার|bag|ব্যাগ|box|বক্স)?");
        Matcher m = p.matcher(text);
        List<Double> nums = new ArrayList<>();
        while (m.find()) {
            try {
                nums.add(Double.parseDouble(m.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return nums.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private Map<String, Object> buildItemMap(Product product, double quantity) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", product.getProductId());
        item.put("nameEn", product.getNameEn());
        item.put("nameBn", product.getNameBn());
        item.put("sellingPrice", product.getSellingPrice());
        item.put("stockQty", product.getStockQty());
        item.put("unit", product.getUnit() == null ? "pcs" : product.getUnit());
        item.put("quantity", quantity);
        return item;
    }
}
