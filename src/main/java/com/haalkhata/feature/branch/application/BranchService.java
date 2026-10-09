package com.haalkhata.feature.branch.application;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.branch.domain.*;
import com.haalkhata.feature.branch.infrastructure.*;
import com.haalkhata.feature.auth.infrastructure.AppUserRepository;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class BranchService {
    private final BranchRepository branches;
    private final BranchMemberRepository members;
    private final AppUserRepository users;
    public record UpsertRequest(String branchId, String name, String address, String phone, Boolean isActive, Boolean isDefault) {}
    public List<Map<String,Object>> list(CurrentUser user) { return branches.findByShopIdOrderByIsDefaultDescNameAsc(user.getShopId()).stream().map(this::json).toList(); }
    @Transactional public Map<String,Object> upsert(CurrentUser user, UpsertRequest request) {
        requireOwner(user);
        if (request.name() == null || request.name().isBlank()) throw BusinessException.badRequest("Branch name is required");
        Branch b = request.branchId() == null || request.branchId().isBlank()
                ? Branch.builder().branchId(Ids.uuid()).shopId(user.getShopId()).isActive(true).isDefault(false).createdAt(LocalDateTime.now()).build()
                : branches.findByBranchIdAndShopId(request.branchId(), user.getShopId()).orElseThrow(() -> BusinessException.notFound("Branch not found"));
        b.setName(request.name().trim()); b.setAddress(blank(request.address())); b.setPhone(blank(request.phone()));
        if (request.isActive() != null) b.setActive(request.isActive());
        if (request.isDefault() != null && request.isDefault()) {
            branches.findByShopIdOrderByIsDefaultDescNameAsc(user.getShopId()).forEach(other -> { other.setDefault(false); other.setUpdatedAt(LocalDateTime.now()); });
            b.setDefault(true);
        }
        b.setUpdatedAt(LocalDateTime.now()); Branch saved = branches.save(b);
        // Authentication is provided by the shared auth service. Its owner may
        // legitimately not have a mirrored row in this service's legacy
        // `users` table, while branch_members has a foreign key to that table.
        // Owners already have access through their JWT role, so only create a
        // membership when the local user exists; otherwise this FK insert turns
        // a valid branch creation into an opaque 500 response.
        if (users.existsById(user.getUserId())
                && members.findByBranchIdAndUserId(saved.getBranchId(), user.getUserId()).isEmpty()) {
            members.save(BranchMember.builder().membershipId(Ids.uuid())
                    .branchId(saved.getBranchId()).userId(user.getUserId()).build());
        }
        return json(saved);
    }
    @Transactional public void assign(CurrentUser owner, String branchId, String userId) {
        requireOwner(owner); Branch b = branches.findByBranchIdAndShopId(branchId, owner.getShopId()).orElseThrow(() -> BusinessException.notFound("Branch not found"));
        users.findByIdAndShopId(userId, owner.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Staff member not found in this shop"));
        if (members.findByBranchIdAndUserId(b.getBranchId(), userId).isEmpty()) members.save(BranchMember.builder().membershipId(Ids.uuid()).branchId(b.getBranchId()).userId(userId).build());
    }
    private void requireOwner(CurrentUser u) { if (u.getRole() == null || !(u.getRole().equalsIgnoreCase("OWNER") || u.getRole().equalsIgnoreCase("ADMIN"))) throw BusinessException.forbidden("Owner access required"); }
    private String blank(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private Map<String,Object> json(Branch b) { Map<String,Object> o = new LinkedHashMap<>(); o.put("branchId",b.getBranchId()); o.put("shopId",b.getShopId()); o.put("name",b.getName()); o.put("address",b.getAddress()); o.put("phone",b.getPhone()); o.put("isDefault",b.isDefault()); o.put("isActive",b.isActive()); o.put("createdAt",b.getCreatedAt()); o.put("updatedAt",b.getUpdatedAt()); return o; }
}
