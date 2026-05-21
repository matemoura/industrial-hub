package com.industrialhub.backend.common.plant;

import com.industrialhub.backend.common.application.PlantContext;
import com.industrialhub.backend.common.infrastructure.UserPlantRepository;
import com.industrialhub.backend.common.presentation.PlantContextFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlantContextFilterTest {

    @Mock
    UserPlantRepository userPlantRepository;

    @InjectMocks
    PlantContextFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        PlantContext.clear();
    }

    // --- MF-S23-01: ADMIN uses GrantedAuthority, no DB query needed ---

    @Test
    void admin_setsAdminContext_withoutQueryingDatabase() throws Exception {
        setAuthentication("admin", "ROLE_ADMIN");

        final boolean[] capturedIsAdmin = new boolean[1];
        FilterChain capturingChain = (req, res) -> capturedIsAdmin[0] = PlantContext.isAdminContext();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), capturingChain);

        assertThat(capturedIsAdmin[0]).isTrue();
        // No DB call — role read from GrantedAuthority in the JWT
        verifyNoInteractions(userPlantRepository);
    }

    @Test
    void admin_contextClearedAfterFilter() throws Exception {
        setAuthentication("admin", "ROLE_ADMIN");

        filter.doFilter(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            new MockFilterChain()
        );

        // After filter completes, context must be cleared (finally block)
        assertThat(PlantContext.isAdminContext()).isFalse();
        assertThat(PlantContext.current()).isEmpty();
    }

    // --- MF-S23-01: non-ADMIN with plants sets correct plant IDs ---

    @Test
    void operator_withPlants_setsPlantContextDuringExecution() throws Exception {
        UUID plantId = UUID.randomUUID();
        setAuthentication("operator", "ROLE_OPERATOR");
        when(userPlantRepository.findPlantIdsByUsername("operator")).thenReturn(List.of(plantId));

        final List<UUID>[] capturedPlantIds = new List[1];
        final boolean[] capturedIsAdmin = new boolean[1];
        FilterChain capturingChain = (req, res) -> {
            capturedIsAdmin[0] = PlantContext.isAdminContext();
            capturedPlantIds[0] = PlantContext.current();
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), capturingChain);

        assertThat(capturedIsAdmin[0]).isFalse();
        assertThat(capturedPlantIds[0]).containsExactly(plantId);
    }

    // --- MF-S23-01: non-ADMIN with no plant associations → empty list + warn (no fail-open) ---

    @Test
    void operator_withNoPlantAssociations_setsEmptyListContext() throws Exception {
        setAuthentication("operator", "ROLE_OPERATOR");
        when(userPlantRepository.findPlantIdsByUsername("operator")).thenReturn(List.of());

        final List<UUID>[] capturedPlantIds = new List[1];
        final boolean[] capturedIsAdmin = new boolean[1];
        FilterChain capturingChain = (req, res) -> {
            capturedIsAdmin[0] = PlantContext.isAdminContext();
            capturedPlantIds[0] = PlantContext.current();
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), capturingChain);

        // Must NOT silently fail-open: context must be explicitly set to empty list
        assertThat(capturedIsAdmin[0]).isFalse();
        assertThat(capturedPlantIds[0]).isEmpty();
        verify(userPlantRepository).findPlantIdsByUsername("operator");
    }

    // --- Context is cleared even when exception occurs in filter chain ---

    @Test
    void contextClearedEvenWhenChainThrows() throws Exception {
        setAuthentication("admin", "ROLE_ADMIN");

        FilterChain throwingChain = (req, res) -> { throw new RuntimeException("chain error"); };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), throwingChain);
        } catch (RuntimeException ignored) {
            // expected
        }

        // finally block must have cleared the context
        assertThat(PlantContext.isAdminContext()).isFalse();
        assertThat(PlantContext.current()).isEmpty();
    }

    // --- Unauthenticated request: filter passes through without setting context ---

    @Test
    void unauthenticatedRequest_doesNotSetContext() throws Exception {
        // No authentication set in SecurityContextHolder
        final boolean[] capturedIsAdmin = new boolean[1];
        final List<UUID>[] capturedPlantIds = new List[1];

        FilterChain capturingChain = (req, res) -> {
            capturedIsAdmin[0] = PlantContext.isAdminContext();
            capturedPlantIds[0] = PlantContext.current();
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), capturingChain);

        assertThat(capturedIsAdmin[0]).isFalse();
        assertThat(capturedPlantIds[0]).isEmpty();
        verifyNoInteractions(userPlantRepository);
    }

    private void setAuthentication(String username, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            username, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
