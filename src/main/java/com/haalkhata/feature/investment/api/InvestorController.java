package com.haalkhata.feature.investment.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.investment.application.InvestmentService;
import com.haalkhata.feature.investment.application.InvestmentService.InvestorRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investors")
@RequiredArgsConstructor
public class InvestorController {

    private final InvestmentService investmentService;

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user) {
        return investmentService.listInvestors(user);
    }

    @GetMapping("/{investorId}")
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user, @PathVariable String investorId) {
        return investmentService.getInvestor(user, investorId);
    }

    @GetMapping("/{investorId}/payouts")
    public List<Map<String, Object>> payouts(@AuthenticationPrincipal CurrentUser user,
                                             @PathVariable String investorId) {
        return investmentService.payoutsForInvestor(user, investorId);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody InvestorRequest request) {
        return investmentService.upsertInvestor(user, request);
    }

    @PutMapping("/{investorId}")
    public Map<String, Object> update(@AuthenticationPrincipal CurrentUser user,
                                      @PathVariable String investorId,
                                      @RequestBody InvestorRequest request) {
        return investmentService.upsertInvestor(user, withId(request, investorId));
    }

    @DeleteMapping("/{investorId}")
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String investorId) {
        investmentService.deleteInvestor(user, investorId);
    }

    private InvestorRequest withId(InvestorRequest request, String investorId) {
        return new InvestorRequest(investorId, request.name(), request.phone(), request.email(),
                request.address(), request.photoUrl(), request.notes());
    }
}
