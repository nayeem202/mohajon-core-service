package com.haalkhata.feature.assistant.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.assistant.application.GeminiAskService;
import com.haalkhata.feature.assistant.application.GeminiBillService;
import com.haalkhata.feature.assistant.application.GeminiCommandService;
import com.haalkhata.feature.assistant.application.TtsService;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final GeminiCommandService geminiCommandService;
    private final GeminiBillService geminiBillService;
    private final GeminiAskService geminiAskService;
    private final TtsService ttsService;

    @PostMapping("/command")
    public Map<String, Object> command(@RequestBody Map<String, String> request) {
        return geminiCommandService.interpret(request.get("text"));
    }

    @PostMapping("/bill")
    public Map<String, Object> bill(@AuthenticationPrincipal CurrentUser user,
                                   @RequestBody Map<String, String> request) {
        return geminiBillService.interpretBill(user, request.get("text"));
    }

    @PostMapping("/bill-audio")
    public Map<String, Object> billAudio(@AuthenticationPrincipal CurrentUser user,
                                        @RequestBody Map<String, String> request) {
        String audioB64 = request.get("audio");
        if (audioB64 == null || audioB64.isBlank()) {
            throw com.haalkhata.core.exception.BusinessException.badRequest("Voice audio is empty.");
        }
        byte[] audio;
        try {
            audio = Base64.getDecoder().decode(audioB64);
        } catch (IllegalArgumentException ex) {
            throw com.haalkhata.core.exception.BusinessException.badRequest("Invalid audio data.");
        }
        return geminiBillService.interpretBillAudio(user, request.get("mimeType"), audio);
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(@AuthenticationPrincipal CurrentUser user,
                                  @RequestBody Map<String, String> request) {
        return geminiAskService.answer(user, request.get("text"), request.get("stats"));
    }

    @PostMapping("/ask-audio")
    public Map<String, Object> askAudio(@RequestBody Map<String, String> request) {
        String audioB64 = request.get("audio");
        if (audioB64 == null || audioB64.isBlank()) {
            throw com.haalkhata.core.exception.BusinessException.badRequest("Voice audio is empty.");
        }
        byte[] audio;
        try {
            audio = Base64.getDecoder().decode(audioB64);
        } catch (IllegalArgumentException ex) {
            throw com.haalkhata.core.exception.BusinessException.badRequest("Invalid audio data.");
        }
        return geminiAskService.answerAudio(request.get("mimeType"), audio, request.get("stats"));
    }

    @PostMapping("/tts")
    public Map<String, Object> tts(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.isBlank()) {
            throw com.haalkhata.core.exception.BusinessException.badRequest("Text to speak is empty.");
        }
        var segments = ttsService.synthesize(text)
                .stream()
                .map(bytes -> Base64.getEncoder().encodeToString(bytes))
                .toList();
        return Map.of("segments", segments);
    }
}
