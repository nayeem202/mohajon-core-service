package com.haalkhata.feature.investment.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.investment.application.InvestmentService;
import com.haalkhata.feature.investment.application.InvestmentService.CategoryRequest;
import com.haalkhata.feature.investment.application.InvestmentService.CreateInvestmentRequest;
import com.haalkhata.feature.investment.application.InvestmentService.PayoutRequest;
import com.haalkhata.feature.investment.application.InvestmentService.ProfitRequest;
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
@RequestMapping("/api/v1/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user) {
        return investmentService.listInvestments(user);
    }

    @GetMapping("/{investmentId}")
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user, @PathVariable String investmentId) {
        return investmentService.getInvestment(user, investmentId);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody CreateInvestmentRequest request) {
        return investmentService.createInvestment(user, request);
    }

    @DeleteMapping("/{investmentId}")
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String investmentId) {
        investmentService.deleteInvestment(user, investmentId);
    }

    @GetMapping("/{investmentId}/payouts")
    public List<Map<String, Object>> payouts(@AuthenticationPrincipal CurrentUser user,
                                             @PathVariable String investmentId) {
        return investmentService.payoutsForInvestment(user, investmentId);
    }

    @PostMapping("/{investmentId}/payouts")
    public Map<String, Object> recordPayout(@AuthenticationPrincipal CurrentUser user,
                                            @PathVariable String investmentId,
                                            @Valid @RequestBody PayoutRequest request) {
        return investmentService.recordPayout(user,
                new PayoutRequest(investmentId, request.investorId(), request.amount(), request.note()));
    }

    @PostMapping("/{investmentId}/profit")
    public Map<String, Object> updateProfit(@AuthenticationPrincipal CurrentUser user,
                                            @PathVariable String investmentId,
                                            @Valid @RequestBody ProfitRequest request) {
        return investmentService.updateInvestmentProfit(user, investmentId, request);
    }

    // -------- Categories --------

    @GetMapping("/categories/all")
    public List<Map<String, Object>> categories(@AuthenticationPrincipal CurrentUser user) {
        return investmentService.listCategories(user);
    }

    @PostMapping("/categories")
    public Map<String, Object> createCategory(@AuthenticationPrincipal CurrentUser user,
                                              @Valid @RequestBody CategoryRequest request) {
        return investmentService.upsertCategory(user, request);
    }

    @PutMapping("/categories/{id}")
    public Map<String, Object> updateCategory(@AuthenticationPrincipal CurrentUser user,
                                              @PathVariable String id,
                                              @RequestBody CategoryRequest request) {
        return investmentService.upsertCategory(user, withCategoryId(request, id));
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        investmentService.deleteCategory(user, id);
    }

    private CategoryRequest withCategoryId(CategoryRequest request, String id) {
        return new CategoryRequest(id, request.name(), request.icon(), request.color());
    }
}
