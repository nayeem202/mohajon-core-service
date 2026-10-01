package com.haalkhata.feature.growth.api;
import com.haalkhata.core.security.CurrentUser; import com.haalkhata.feature.growth.application.CustomerGrowthService; import java.util.Map; import lombok.RequiredArgsConstructor; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/customer-growth") @RequiredArgsConstructor
public class CustomerGrowthController { private final CustomerGrowthService service; @GetMapping public Map<String,Object> dashboard(@AuthenticationPrincipal CurrentUser user){return service.dashboard(user);} }
