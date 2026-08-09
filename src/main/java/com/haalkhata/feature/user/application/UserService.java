package com.haalkhata.feature.user.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.auth.domain.AppUser;
import com.haalkhata.feature.auth.infrastructure.AppUserRepository;
import com.haalkhata.feature.user.domain.Menu;
import com.haalkhata.feature.user.domain.UserMenu;
import com.haalkhata.feature.user.domain.UserRole;
import com.haalkhata.feature.user.infrastructure.MenuRepository;
import com.haalkhata.feature.user.infrastructure.RoleRepository;
import com.haalkhata.feature.user.infrastructure.UserMenuRepository;
import com.haalkhata.feature.user.infrastructure.UserRoleRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final MenuRepository menuRepository;
    private final UserMenuRepository userMenuRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public record InviteRequest(String name, String phone, String email, String password,
                                String role, List<String> menuCodes, String photoUrl) {
    }

    public record UpdateRequest(String name, String phone, String role, List<String> menuCodes,
                                String photoUrl) {
    }

    public List<Map<String, Object>> listStaff(CurrentUser owner) {
        return userRepository.findAll().stream()
                .filter(u -> u.getShopId() != null && u.getShopId().equals(owner.getShopId()))
                .map(this::toJson)
                .toList();
    }

    public List<Map<String, Object>> listRoles() {
        return roleRepository.findAll().stream()
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .map(role -> {
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("id", role.getId());
                    json.put("code", role.getCode());
                    json.put("name", role.getName());
                    return json;
                })
                .toList();
    }

    public List<Map<String, Object>> listMenus() {
        return menuRepository.findAllByOrderBySortOrderAsc().stream()
                .map(menu -> {
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("id", menu.getId());
                    json.put("code", menu.getCode());
                    json.put("name", menu.getName());
                    json.put("section", menu.getSection());
                    json.put("sortOrder", menu.getSortOrder());
                    return json;
                })
                .toList();
    }

    public List<String> myMenuCodes(CurrentUser currentUser) {
        String role = currentUser.getRole() == null ? "" : currentUser.getRole().toUpperCase();
        if ("OWNER".equals(role) || "ADMIN".equals(role)) {
            return menuRepository.findAll().stream().map(Menu::getCode).toList();
        }
        List<String> codes = userMenuRepository.findMenuCodesByUserId(currentUser.getUserId());
        return codes.isEmpty() ? List.of() : codes;
    }

    public Map<String, Object> me(CurrentUser currentUser) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("userId", currentUser.getUserId());
        json.put("name", currentUser.getName() == null ? "" : currentUser.getName());
        json.put("email", currentUser.getEmail() == null ? "" : currentUser.getEmail());
        String role = currentUser.getRole() == null ? "" : currentUser.getRole().toUpperCase();
        if (role.isEmpty()) {
            role = "OWNER";
        }
        json.put("role", role);
        json.put("shopId", currentUser.getShopId());
        json.put("menus", myMenuCodes(currentUser));
        return json;
    }

    @Transactional
    public Map<String, Object> invite(CurrentUser owner, InviteRequest request) {
        if (request.email() != null && userRepository.existsByEmailIgnoreCase(request.email())) {
            throw BusinessException.conflict("A user with this email already exists");
        }
        String roleCode = request.role() == null || request.role().isBlank() ? "STAFF" : request.role().toUpperCase();
        if (roleRepository.findByCode(roleCode).isEmpty()) {
            throw BusinessException.badRequest("Unknown role: " + roleCode);
        }
        String userId = Ids.uuid();
        AppUser staff = AppUser.builder()
                .id(userId)
                .name(request.name().trim())
                .email(request.email() == null ? null : request.email().trim().toLowerCase())
                .phone(request.phone() == null ? null : request.phone().trim())
                .passwordHash(request.password() == null || request.password().isBlank()
                        ? null : passwordEncoder.encode(request.password()))
                .photoUrl(request.photoUrl() == null || request.photoUrl().isBlank()
                        ? null : request.photoUrl())
                .emailVerified(true)
                .isNewUser(false)
                .provider("local")
                .shopId(owner.getShopId())
                .role(roleCode)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(staff);
        roleRepository.findByCode(roleCode).ifPresent(role -> userRoleRepository.save(
                UserRole.builder().id(Ids.uuid()).userId(userId).roleId(role.getId()).build()));
        saveMenuGrants(userId, request.menuCodes());
        syncAuthUser(staff, request.password());
        log.info("STAFF_INVITED userId={} shopId={} role={} menus={}", userId, owner.getShopId(), roleCode,
                request.menuCodes() == null ? List.of() : request.menuCodes());
        return toJson(staff);
    }

    @Transactional
    public Map<String, Object> update(CurrentUser owner, String userId, UpdateRequest request) {
        AppUser staff = userRepository.findById(userId)
                .filter(u -> u.getShopId() != null && u.getShopId().equals(owner.getShopId()))
                .orElseThrow(() -> BusinessException.notFound("Staff member not found"));
        if (request.name() != null) {
            staff.setName(request.name().trim());
        }
        if (request.phone() != null) {
            staff.setPhone(request.phone().trim());
        }
        if (request.role() != null && !request.role().isBlank()) {
            String roleCode = request.role().toUpperCase();
            if (roleRepository.findByCode(roleCode).isEmpty()) {
                throw BusinessException.badRequest("Unknown role: " + roleCode);
            }
            staff.setRole(roleCode);
        }
        if (request.photoUrl() != null) {
            staff.setPhotoUrl(request.photoUrl().isBlank() ? null : request.photoUrl().trim());
        }
        staff.setUpdatedAt(LocalDateTime.now());
        userRepository.save(staff);
        if (request.menuCodes() != null) {
            userMenuRepository.deleteByUserId(staff.getId());
            saveMenuGrants(staff.getId(), request.menuCodes());
        }
        syncAuthUser(staff, null);
        log.info("STAFF_UPDATED userId={} by={}", userId, owner.getUserId());
        return toJson(staff);
    }

    @Transactional
    public void remove(CurrentUser owner, String userId) {
        AppUser staff = userRepository.findById(userId)
                .filter(u -> u.getShopId() != null && u.getShopId().equals(owner.getShopId()))
                .orElseThrow(() -> BusinessException.notFound("Staff member not found"));
        if (staff.getId().equals(owner.getUserId())) {
            throw BusinessException.badRequest("You cannot remove yourself");
        }
        userRoleRepository.deleteByUserId(staff.getId());
        userMenuRepository.deleteByUserId(staff.getId());
        userRepository.delete(staff);
        deleteAuthUser(staff.getId());
        log.info("STAFF_REMOVED userId={} by={}", userId, owner.getUserId());
    }

    // -----------------------------------------------------------------
    // Auth-service (public schema) sync: lets staff sign in with the
    // same email/password through the auth service's login endpoint.
    // -----------------------------------------------------------------

    private void syncAuthUser(AppUser staff, String rawPassword) {
        try {
            boolean exists = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM public.users WHERE external_id = ?)",
                    Boolean.class, staff.getId()));
            if (exists) {
                jdbcTemplate.update("""
                        UPDATE public.users
                           SET display_name = ?, phone_number = ?,
                               password_hash = COALESCE(?, password_hash),
                               email_verified = TRUE, updated_at = NOW()
                         WHERE external_id = ?
                        """, staff.getName(), staff.getPhone(),
                        rawPassword == null || rawPassword.isBlank() ? null
                                : passwordEncoder.encode(rawPassword),
                        staff.getId());
            } else {
                String roleName = staff.getRole() == null ? "STAFF" : staff.getRole().toUpperCase();
                Long roleId = jdbcTemplate.queryForObject(
                        "SELECT id FROM public.roles WHERE name = ?",
                        Long.class, roleName);
                if (roleId == null) {
                    roleId = jdbcTemplate.queryForObject(
                            "SELECT id FROM public.roles WHERE name = 'STAFF'",
                            Long.class);
                }
                Long userId = jdbcTemplate.queryForObject("""
                        INSERT INTO public.users
                            (external_id, email, phone_number, password_hash, display_name,
                             email_verified, phone_verified, provider, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, TRUE, FALSE, 'local', 'ACTIVE', NOW(), NOW())
                        RETURNING id
                        """, Long.class,
                        staff.getId(), staff.getEmail(), staff.getPhone(),
                        rawPassword == null || rawPassword.isBlank() ? null
                                : passwordEncoder.encode(rawPassword),
                        staff.getName());
                jdbcTemplate.update("""
                        INSERT INTO public.user_roles (user_id, role_id)
                        VALUES (?, ?)
                        """, userId, roleId);
            }
            log.info("AUTH_USER_SYNCED externalId={}", staff.getId());
        } catch (Exception ex) {
            log.warn("AUTH_USER_SYNC_FAILED externalId={}: {}", staff.getId(), ex.getMessage());
        }
    }

    private void deleteAuthUser(String externalId) {
        try {
            jdbcTemplate.update("DELETE FROM public.users WHERE external_id = ?", externalId);
            log.info("AUTH_USER_DELETED externalId={}", externalId);
        } catch (Exception ex) {
            log.warn("AUTH_USER_DELETE_FAILED externalId={}: {}", externalId, ex.getMessage());
        }
    }

    private void saveMenuGrants(String userId, List<String> menuCodes) {
        if (menuCodes == null || menuCodes.isEmpty()) {
            return;
        }
        List<Menu> menus = menuRepository.findByCodeIn(menuCodes);
        if (menus.size() != menuCodes.size()) {
            List<String> found = menus.stream().map(Menu::getCode).toList();
            String unknown = menuCodes.stream().filter(c -> !found.contains(c)).toList().toString();
            throw BusinessException.badRequest("Unknown menu codes: " + unknown);
        }
        for (Menu menu : menus) {
            userMenuRepository.save(UserMenu.builder()
                    .id(Ids.uuid())
                    .userId(userId)
                    .menuId(menu.getId())
                    .build());
        }
    }

    private Map<String, Object> toJson(AppUser u) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("userId", u.getId());
        json.put("name", u.getName() == null ? "" : u.getName());
        json.put("phone", u.getPhone() == null ? "" : u.getPhone());
        json.put("email", u.getEmail() == null ? "" : u.getEmail());
        json.put("role", u.getRole() == null ? "STAFF" : u.getRole());
        json.put("shopId", u.getShopId() == null ? "" : u.getShopId());
        json.put("photoUrl", u.getPhotoUrl() == null ? "" : u.getPhotoUrl());
        json.put("updatedAt", u.getUpdatedAt().toString());
        json.put("menus", userMenuRepository.findMenuCodesByUserId(u.getId()));
        return json;
    }
}
