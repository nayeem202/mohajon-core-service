package com.haalkhata.feature.cashflow.api;
import com.haalkhata.core.security.CurrentUser; import com.haalkhata.feature.cashflow.application.CashflowRadarService; import java.util.Map; import lombok.RequiredArgsConstructor; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/cashflow") @RequiredArgsConstructor
public class CashflowRadarController { private final CashflowRadarService service; @GetMapping("/radar") public Map<String,Object> radar(@AuthenticationPrincipal CurrentUser user,@RequestParam(defaultValue="0") double salesChangePercent){return service.forecast(user,Math.max(-100,Math.min(100,salesChangePercent)));} }
