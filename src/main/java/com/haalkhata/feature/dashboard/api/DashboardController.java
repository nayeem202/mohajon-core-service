package com.haalkhata.feature.dashboard.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.dashboard.application.DashboardService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public Map<String, Object> summary(@AuthenticationPrincipal CurrentUser user) {
        return dashboardService.summary(user);
    }
}
