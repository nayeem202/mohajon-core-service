package com.haalkhata.feature.support.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.support.application.SupportInboxService;
import com.haalkhata.feature.support.application.SupportInboxService.MessageRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportInboxController {
    private final SupportInboxService support;

    @GetMapping("/access")
    public Map<String, Object> access(@AuthenticationPrincipal CurrentUser user) { return support.access(user); }
    @GetMapping("/tickets")
    public Map<String, Object> mine(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return support.myTickets(user, page, size);
    }
    @PostMapping("/tickets")
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user, @RequestBody MessageRequest request) {
        return support.createTicket(user, request);
    }
    @GetMapping("/tickets/{ticketId}/messages")
    public Map<String, Object> messages(@AuthenticationPrincipal CurrentUser user, @PathVariable String ticketId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return support.ticketMessages(user, ticketId, page, size);
    }
    @PostMapping("/tickets/{ticketId}/messages")
    public Map<String, Object> send(@AuthenticationPrincipal CurrentUser user, @PathVariable String ticketId,
            @RequestBody MessageRequest request) { return support.addOwnerMessage(user, ticketId, request); }
    @GetMapping("/admin/tickets")
    public Map<String, Object> adminTickets(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return support.adminTickets(user, page, size);
    }
    @PostMapping("/admin/tickets/{ticketId}/messages")
    public Map<String, Object> adminSend(@AuthenticationPrincipal CurrentUser user, @PathVariable String ticketId,
            @RequestBody MessageRequest request) { return support.addAdminMessage(user, ticketId, request); }
}
