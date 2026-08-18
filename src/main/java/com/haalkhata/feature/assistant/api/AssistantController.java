package com.haalkhata.feature.assistant.api;

import com.haalkhata.feature.assistant.application.GeminiCommandService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final GeminiCommandService geminiCommandService;

    @PostMapping("/command")
    public Map<String, Object> command(@RequestBody Map<String, String> request) {
        return geminiCommandService.interpret(request.get("text"));
    }
}
