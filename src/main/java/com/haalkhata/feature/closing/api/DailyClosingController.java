package com.haalkhata.feature.closing.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.closing.application.DailyClosingService;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/day-closing") @RequiredArgsConstructor
public class DailyClosingController {
    private final DailyClosingService service;
    @GetMapping public Map<String,Object> current(@AuthenticationPrincipal CurrentUser user,@RequestParam(required=false) LocalDate date){return service.current(user,date==null?LocalDate.now():date);}
    @GetMapping("/history") public List<Map<String,Object>> history(@AuthenticationPrincipal CurrentUser user){return service.history(user);}
    @PostMapping public Map<String,Object> close(@AuthenticationPrincipal CurrentUser user,@RequestBody CloseRequest request){return service.close(user,request.openingCash(),request.actualCash(),request.note());}
    public record CloseRequest(double openingCash,double actualCash,String note){}
}
