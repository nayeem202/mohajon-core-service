package com.haalkhata.core.security;

import com.haalkhata.feature.auth.domain.AppUser;
import com.haalkhata.feature.auth.infrastructure.AppUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                String userId = claims.getSubject();
                if (userId == null || userId.isBlank()) {
                    chain.doFilter(request, response);
                    return;
                }
                String email = claims.get("email", String.class);
                String name = claims.get("name", String.class);
                String claimShopId = claims.get("shopId", String.class);
                String claimRole = claims.get("role", String.class);
                @SuppressWarnings("unchecked")
                List<String> claimRoles = claims.get("roles", List.class);

                // DB is authoritative: active users whose shopId/role may have
                // changed get the fresh values, and deleted/inactive users are
                // rejected even if their JWT is still unexpired. Users that only
                // exist in the auth-service (not yet provisioned here) fall back
                // to the token claims.
                String shopId = claimShopId;
                String role = claimRole;
                List<String> roles = claimRoles;
                var dbUser = userRepository.findById(userId).orElse(null);
                if (dbUser != null) {
                    if (!"ACTIVE".equals(dbUser.getStatus())) {
                        log.debug("JWT rejected: user {} is {}", userId, dbUser.getStatus());
                        chain.doFilter(request, response);
                        return;
                    }
                    if (dbUser.getShopId() != null && !dbUser.getShopId().isBlank()) {
                        shopId = dbUser.getShopId();
                    }
                    if (dbUser.getRole() != null && !dbUser.getRole().isBlank()) {
                        role = dbUser.getRole();
                    }
                }
                if (shopId == null || shopId.isBlank()) {
                    shopId = dbUser != null && dbUser.getShopId() != null && !dbUser.getShopId().isBlank()
                            ? dbUser.getShopId()
                            : resolveShopId(userId);
                }
                if (role == null && roles != null && !roles.isEmpty()) {
                    role = roles.get(0);
                }
                if (role == null) {
                    role = "OWNER";
                }
                CurrentUser user = new CurrentUser(userId, email, name, shopId, role, roles);
                request.setAttribute("currentUser", user);
                var authorities = (roles == null ? List.of(role) : roles)
                        .stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
                var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Invalid JWT: {}", ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveShopId(String userId) {
        return userRepository.findById(userId)
                .map(AppUser::getShopId)
                .filter(s -> s != null && !s.isBlank())
                .orElse("shop_" + userId);
    }
}
