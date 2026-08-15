package com.haalkhata.feature.notification.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.notification.application.NotificationService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/history")
    public java.util.List<Map<String, Object>> history(
            @AuthenticationPrincipal CurrentUser user) {
        return notificationService.history(user);
    }

    @DeleteMapping("/history/{id}")
    public Map<String, Object> deleteForMe(@AuthenticationPrincipal CurrentUser user,
                                           @PathVariable String id) {
        notificationService.deleteForUser(user, id);
        return Map.of("deleted", true);
    }

    @DeleteMapping("/history/{id}/everyone")
    public Map<String, Object> deleteForEveryone(@AuthenticationPrincipal CurrentUser user,
                                                 @PathVariable String id) {
        notificationService.deleteForEveryone(user, id);
        return Map.of("deleted", true, "everyone", true);
    }

    @PostMapping("/devices")
    public Map<String, Object> registerDevice(@AuthenticationPrincipal CurrentUser user,
                                              @RequestBody Map<String, Object> request) {
        var device = notificationService.registerDevice(user, (String) request.get("token"),
                (String) request.get("platform"), request.get("enabledTypes"));
        return Map.of("tokenId", device.getTokenId(), "registered", true);
    }

    @PostMapping("/devices/preferences")
    public Map<String, Object> updatePreferences(@AuthenticationPrincipal CurrentUser user,
                                                 @RequestBody Map<String, Object> request) {
        notificationService.updatePreferences(user, (String) request.get("token"),
                request.get("enabledTypes"));
        return Map.of("updated", true);
    }

    @PostMapping("/devices/disable")
    public Map<String, Object> disableDevice(@AuthenticationPrincipal CurrentUser user,
                                             @RequestBody Map<String, String> request) {
        notificationService.disableDevice(user, request.get("token"));
        return Map.of("disabled", true);
    }

    @PostMapping("/send")
    public Map<String, Object> send(@AuthenticationPrincipal CurrentUser user,
                                    @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        var data = (Map<String, String>) request.getOrDefault("data", Map.of());
        return notificationService.send(user, (String) request.get("title"),
                (String) request.get("body"), data);
    }
}
