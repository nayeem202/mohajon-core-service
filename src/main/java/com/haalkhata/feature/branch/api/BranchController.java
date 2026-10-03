package com.haalkhata.feature.branch.api;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.branch.application.BranchService;
import com.haalkhata.feature.branch.application.BranchService.UpsertRequest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/branches") @RequiredArgsConstructor
public class BranchController {
    private final BranchService service;
    @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal CurrentUser u) { return service.list(u); }
    @PostMapping public Map<String,Object> create(@AuthenticationPrincipal CurrentUser u, @RequestBody UpsertRequest r) { return service.upsert(u,r); }
    @PutMapping("/{id}") public Map<String,Object> update(@AuthenticationPrincipal CurrentUser u, @PathVariable String id, @RequestBody UpsertRequest r) { return service.upsert(u,new UpsertRequest(id,r.name(),r.address(),r.phone(),r.isActive(),r.isDefault())); }
    @PostMapping("/{branchId}/members/{userId}") public void assign(@AuthenticationPrincipal CurrentUser u,@PathVariable String branchId,@PathVariable String userId) { service.assign(u,branchId,userId); }
}
