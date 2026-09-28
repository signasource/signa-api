package com.signasource.signa_api.organizations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.CreateInviteCodeRequest;
import com.signasource.signa_api.organizations.dto.CreateOrganizationRequest;
import com.signasource.signa_api.organizations.dto.InviteCodeResponse;
import com.signasource.signa_api.organizations.dto.OrganizationResponse;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeRequest;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeResponse;
import com.signasource.signa_api.organizations.service.InviteCodeService;
import com.signasource.signa_api.organizations.service.OrganizationService;
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
class OrganizationControllerTest {

    @Mock private OrganizationService organizationService;
    @Mock private InviteCodeService inviteCodeService;

    @InjectMocks private OrganizationController organizationController;

    private User mockUser;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockUser = mock(User.class);
        mockUserDetails = mock(CustomUserDetails.class);
    }

    @Test
    void createOrganization_ShouldReturn201() {
        CreateOrganizationRequest request = new CreateOrganizationRequest("Hospital San Martín");
        OrganizationResponse response =
                new OrganizationResponse(UUID.randomUUID(), "Hospital San Martín");
        when(organizationService.createOrganization(request)).thenReturn(response);

        ResponseEntity<OrganizationResponse> result =
                organizationController.createOrganization(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void createInviteCode_ShouldReturn201() {
        UUID organizationId = UUID.randomUUID();
        CreateInviteCodeRequest request = new CreateInviteCodeRequest(UUID.randomUUID(), null, null);
        InviteCodeResponse response =
                new InviteCodeResponse(
                        UUID.randomUUID(), "HSMT2026", organizationId, UUID.randomUUID(), "LSA para Salud",
                        null, null, 0, true);
        when(inviteCodeService.createInviteCode(organizationId, request)).thenReturn(response);

        ResponseEntity<InviteCodeResponse> result =
                organizationController.createInviteCode(organizationId, request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void getInviteCodes_ShouldReturn200() {
        UUID organizationId = UUID.randomUUID();
        List<InviteCodeResponse> responses = List.of();
        when(inviteCodeService.getOrganizationInviteCodes(organizationId)).thenReturn(responses);

        ResponseEntity<List<InviteCodeResponse>> result =
                organizationController.getInviteCodes(organizationId);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(responses, result.getBody());
    }

    @Test
    void redeemInviteCode_ShouldReturn200() {
        when(mockUserDetails.getUser()).thenReturn(mockUser);
        RedeemInviteCodeRequest request = new RedeemInviteCodeRequest("HSMT2026");
        RedeemInviteCodeResponse response =
                new RedeemInviteCodeResponse("Hospital San Martín", null, false, null);
        when(inviteCodeService.redeem(mockUser, "HSMT2026")).thenReturn(response);

        ResponseEntity<RedeemInviteCodeResponse> result =
                organizationController.redeemInviteCode(request, mockUserDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }
}
