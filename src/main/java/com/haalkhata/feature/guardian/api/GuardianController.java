package com.haalkhata.feature.guardian.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.guardian.application.GuardianService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/business-guardian") @RequiredArgsConstructor
public class GuardianController {
    private final GuardianService service;
    @GetMapping public Map<String, Object> dashboard(@AuthenticationPrincipal CurrentUser user) { return service.dashboard(user); }
    @GetMapping("/timeline")
    public Map<String, Object> timeline(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit) {
        return service.timeline(user, cursor, limit);
    }
}
