package com.haalkhata.feature.coach.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.coach.application.BusinessCoachService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business-coach")
@RequiredArgsConstructor
public class BusinessCoachController {
    private final BusinessCoachService service;

    @GetMapping("/history")
    public List<Map<String, Object>> history(@AuthenticationPrincipal CurrentUser user) {
        return service.history(user);
    }

    @PostMapping("/refresh")
    public List<Map<String, Object>> refresh(@AuthenticationPrincipal CurrentUser user) {
        return service.refresh(user.getShopId(), user.getUserId(), user.getBranchId());
    }
}
