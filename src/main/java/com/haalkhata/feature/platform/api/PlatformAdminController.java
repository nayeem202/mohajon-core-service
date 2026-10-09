package com.haalkhata.feature.platform.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.platform.application.PlatformAdminService;
import com.haalkhata.feature.platform.application.PlatformAdminService.PushRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin")
@RequiredArgsConstructor
public class PlatformAdminController {
    private final PlatformAdminService platform;

    @GetMapping("/access")
    public Map<String, Object> access(@AuthenticationPrincipal CurrentUser user) { return platform.access(user); }

    @GetMapping("/overview")
    public Map<String, Object> overview(@AuthenticationPrincipal CurrentUser user) { return platform.overview(user); }

    @GetMapping("/payments")
    public Map<String, Object> payments(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return platform.paymentPage(user, page, size);
    }

    @PostMapping("/payments/{paymentId}/reconcile")
    public Map<String, Object> reconcile(@AuthenticationPrincipal CurrentUser user, @PathVariable String paymentId) {
        return platform.reconcile(user, paymentId);
    }

    @PostMapping("/notifications/send")
    public Map<String, Object> sendPush(@AuthenticationPrincipal CurrentUser user,
                                        @RequestBody PushRequest request) {
        return platform.sendPush(user, request);
    }
}
