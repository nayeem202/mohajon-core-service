package com.haalkhata.feature.customer.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.customer.application.CustomerService;
import com.haalkhata.feature.customer.application.CustomerService.LedgerRequest;
import com.haalkhata.feature.customer.application.CustomerService.UpsertRequest;
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
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user) {
        return customerService.list(user);
    }

    @GetMapping("/{customerId}")
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user, @PathVariable String customerId) {
        return customerService.get(user, customerId);
    }

    @GetMapping("/{customerId}/ledger")
    public List<Map<String, Object>> ledger(@AuthenticationPrincipal CurrentUser user,
                                            @PathVariable String customerId) {
        return customerService.ledger(user, customerId);
    }

    @PostMapping("/{customerId}/ledger")
    public Map<String, Object> addLedger(@AuthenticationPrincipal CurrentUser user,
                                         @PathVariable String customerId,
                                         @Valid @RequestBody LedgerRequest request) {
        return customerService.addLedgerEntry(user, customerId, request);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody UpsertRequest request) {
        return customerService.upsert(user, request);
    }

    @PutMapping("/{customerId}")
    public Map<String, Object> update(@AuthenticationPrincipal CurrentUser user,
                                      @PathVariable String customerId,
                                      @RequestBody UpsertRequest request) {
        return customerService.upsert(user, withId(request, customerId));
    }

    @DeleteMapping("/{customerId}")
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String customerId) {
        customerService.delete(user, customerId);
    }

    private UpsertRequest withId(UpsertRequest request, String customerId) {
        return new UpsertRequest(customerId, request.name(), request.phone(), request.email(),
                request.address(), request.notes(), request.imagePath(), request.totalSpent(),
                request.tag(), request.isDeleted());
    }
}
