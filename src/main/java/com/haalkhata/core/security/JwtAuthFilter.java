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
                String email = claims.get("email", String.class);
                String name = claims.get("name", String.class);
                String shopId = claims.get("shopId", String.class);
                if (shopId == null || shopId.isBlank()) {
                    shopId = resolveShopId(userId);
                }
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                String role = claims.get("role", String.class);
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
