package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminUserResponse;
import com.signasource.signa_api.admin.dto.CreateAdminRequest;
import com.signasource.signa_api.admin.dto.UpdateUserRequest;
import com.signasource.signa_api.admin.service.AdminService;
import com.signasource.signa_api.users.entity.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock private AdminService adminService;
    @InjectMocks private AdminUserController controller;

    private AdminUserResponse makeUser(UUID id) {
        return new AdminUserResponse(
                id, "user@test.com", "user", "Name", "Last", Role.USER, true, true, null);
    }

    @Test
    void shouldListUsers() {
        when(adminService.getUsers(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(makeUser(UUID.randomUUID()))));

        ResponseEntity<?> resp = controller.listUsers(null, null, Pageable.unpaged());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void shouldGetUser() {
        UUID id = UUID.randomUUID();
        when(adminService.getUser(id)).thenReturn(makeUser(id));

        ResponseEntity<AdminUserResponse> resp = controller.getUser(id);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(id, resp.getBody().id());
    }

    @Test
    void shouldUpdateUser() {
        UUID id = UUID.randomUUID();
        UpdateUserRequest req = new UpdateUserRequest(Role.ADMIN, true);
        AdminUserResponse updated =
                new AdminUserResponse(
                        id, "user@test.com", "user", "Name", "Last", Role.ADMIN, true, true, null);
        when(adminService.updateUser(eq(id), any())).thenReturn(updated);

        ResponseEntity<AdminUserResponse> resp = controller.updateUser(id, req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Role.ADMIN, resp.getBody().role());
    }

    @Test
    void shouldListAdmins() {
        when(adminService.getAdmins()).thenReturn(List.of(makeUser(UUID.randomUUID())));

        ResponseEntity<List<AdminUserResponse>> resp = controller.listAdmins();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
    }

    @Test
    void shouldCreateAdmin() {
        UUID id = UUID.randomUUID();
        CreateAdminRequest req = new CreateAdminRequest("new@signa.com", "Nuevo", "Admin", "pass");
        AdminUserResponse created =
                new AdminUserResponse(
                        id,
                        "new@signa.com",
                        "nuevo",
                        "Nuevo",
                        "Admin",
                        Role.ADMIN,
                        true,
                        true,
                        null);
        when(adminService.createAdmin(any())).thenReturn(created);

        ResponseEntity<AdminUserResponse> resp = controller.createAdmin(req);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals("new@signa.com", resp.getBody().email());
    }
}
