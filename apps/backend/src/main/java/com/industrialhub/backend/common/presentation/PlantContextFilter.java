package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.PlantContext;
import com.industrialhub.backend.common.infrastructure.UserPlantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PlantContextFilter.class);

    private final UserPlantRepository userPlantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                String username = auth.getName();

                boolean isAdmin = auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(a -> a.equals("ROLE_ADMIN"));

                if (isAdmin) {
                    // ADMIN sees all plants — no DB query needed, role already in JWT authorities
                    PlantContext.setAdmin();
                } else {
                    List<UUID> plantIds = userPlantRepository.findPlantIdsByUsername(username);
                    if (plantIds.isEmpty()) {
                        log.warn("User {} has no plant associations", username);
                        PlantContext.set(List.of());
                    } else {
                        PlantContext.set(plantIds);
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            // CRITICAL: always clear to prevent thread pool leaks
            PlantContext.clear();
        }
    }
}
