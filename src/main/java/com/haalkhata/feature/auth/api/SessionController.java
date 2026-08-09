package com.haalkhata.feature.auth.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.auth.domain.LoginSession;
import com.haalkhata.feature.auth.infrastructure.LoginSessionRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final LoginSessionRepository loginSessionRepository;

    @GetMapping
    public List<Map<String, Object>> mySessions(@AuthenticationPrincipal CurrentUser user) {
        return loginSessionRepository.findByUserIdOrderByLastLoginAtDesc(user.getUserId())
                .stream().map(this::toJson).toList();
    }

    @DeleteMapping("/{id}")
    public void revoke(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        loginSessionRepository.findById(id)
                .filter(s -> s.getUserId().equals(user.getUserId()))
                .ifPresent(loginSessionRepository::delete);
    }

    private Map<String, Object> toJson(LoginSession s) {
        return Map.of(
                "sessionId", s.getId(),
                "deviceName", s.getDeviceName() == null ? "Unknown device" : s.getDeviceName(),
                "deviceId", s.getDeviceId() == null ? "" : s.getDeviceId(),
                "osVersion", s.getOsVersion() == null ? "" : s.getOsVersion(),
                "manufacturer", s.getManufacturer() == null ? "" : s.getManufacturer(),
                "model", s.getModel() == null ? "" : s.getModel(),
                "locationLabel", s.getLocationLabel() == null ? "" : s.getLocationLabel(),
                "lastLoginAt", s.getLastLoginAt().format(ISO));
    }
}
