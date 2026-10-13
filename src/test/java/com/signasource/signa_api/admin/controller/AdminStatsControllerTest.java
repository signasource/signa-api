package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminMeResponse;
import com.signasource.signa_api.admin.dto.AdminStatsResponse;
import com.signasource.signa_api.admin.service.AdminService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminStatsControllerTest {

    @Mock private AdminService adminService;
    @InjectMocks private AdminStatsController controller;

    @Test
    void shouldReturnMe() {
        User user =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("admin@signa.com")
                        .username("admin")
                        .name("Admin")
                        .lastName("Signa")
                        .role(Role.ADMIN)
                        .enabled(true)
                        .verified(true)
                        .providers(Set.of(AuthProvider.LOCAL))
                        .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        ResponseEntity<AdminMeResponse> resp = controller.me(userDetails);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("admin@signa.com", resp.getBody().email());
    }

    @Test
    void shouldReturnStats() {
        AdminStatsResponse stats = new AdminStatsResponse(5, 2, 4, 100, 0, 20);
        when(adminService.getStats()).thenReturn(stats);

        ResponseEntity<AdminStatsResponse> resp = controller.getStats();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(5, resp.getBody().activeOrganizations());
    }
}
