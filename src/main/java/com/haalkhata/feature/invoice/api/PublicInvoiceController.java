package com.haalkhata.feature.invoice.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.invoice.application.PublicInvoiceService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PublicInvoiceController {
    private final PublicInvoiceService service;

    @PostMapping("/api/v1/invoices/{saleId}/share-link")
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user, @PathVariable String saleId) {
        return service.createLink(user, saleId);
    }

    @GetMapping(value = "/public/invoices/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public String view(@PathVariable String token) {
        return service.render(token);
    }
}
