package com.signasource.signa_api.organizations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.CreateRefundRequestRequest;
import com.signasource.signa_api.organizations.dto.UserRefundRequestResponse;
import com.signasource.signa_api.organizations.entity.RefundRequestReason;
import com.signasource.signa_api.organizations.service.UserRefundRequestService;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserRefundRequestControllerTest {

    @Mock private UserRefundRequestService service;
    @Mock private CustomUserDetails userDetails;

    @InjectMocks private UserRefundRequestController controller;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).build();
        when(userDetails.getUser()).thenReturn(user);
    }

    @Test
    void listReturns200WithUserRequests() {
        List<UserRefundRequestResponse> requests = List.of();
        when(service.listForUser(user)).thenReturn(requests);

        ResponseEntity<List<UserRefundRequestResponse>> response = controller.list(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(requests, response.getBody());
    }

    @Test
    void createForSeatsReturns201() {
        UUID orgId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        CreateRefundRequestRequest request =
                new CreateRefundRequestRequest(RefundRequestReason.MISTAKE, "motivo valido");
        UserRefundRequestResponse created =
                new UserRefundRequestResponse(
                        UUID.randomUUID(),
                        "SEATS",
                        purchaseId,
                        "MISTAKE",
                        "motivo valido",
                        "PENDING",
                        null,
                        null,
                        null,
                        null);
        when(service.createForSeats(orgId, purchaseId, user, request)).thenReturn(created);

        ResponseEntity<UserRefundRequestResponse> response =
                controller.createForSeats(orgId, purchaseId, userDetails, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(created, response.getBody());
    }
}
