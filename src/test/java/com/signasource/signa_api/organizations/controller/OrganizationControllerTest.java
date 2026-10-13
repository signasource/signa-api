package com.signasource.signa_api.organizations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.AddOrganizationAdminRequest;
import com.signasource.signa_api.organizations.dto.AddOrganizationCourseRequest;
import com.signasource.signa_api.organizations.dto.CreateInviteCodeRequest;
import com.signasource.signa_api.organizations.dto.CreateOrganizationRequest;
import com.signasource.signa_api.organizations.dto.InviteByEmailRequest;
import com.signasource.signa_api.organizations.dto.InviteCodeResponse;
import com.signasource.signa_api.organizations.dto.MyOrganizationResponse;
import com.signasource.signa_api.organizations.dto.OrganizationAdminResponse;
import com.signasource.signa_api.organizations.dto.OrganizationCourseResponse;
import com.signasource.signa_api.organizations.dto.OrganizationDetailResponse;
import com.signasource.signa_api.organizations.dto.OrganizationResponse;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeRequest;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeResponse;
import com.signasource.signa_api.organizations.dto.UpdateOrganizationRequest;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.service.InviteCodeService;
import com.signasource.signa_api.organizations.service.OrganizationMemberService;
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
    @Mock private OrganizationMemberService memberService;

    @InjectMocks private OrganizationController organizationController;

    private User mockUser;
    private CustomUserDetails mockUserDetails;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        mockUser = mock(User.class);
        mockUserDetails = mock(CustomUserDetails.class);
        organizationId = UUID.randomUUID();
    }

    private void stubPrincipal() {
        when(mockUserDetails.getUser()).thenReturn(mockUser);
    }

    private InviteCodeResponse inviteCodeResponse() {
        return new InviteCodeResponse(
                UUID.randomUUID(), "HSMT2026", organizationId, null, null, null, null, 0, true);
    }

    @Test
    void shouldReturn201WhenCreatingOrganization() {
        CreateOrganizationRequest request = new CreateOrganizationRequest("Hospital San Martin");
        OrganizationResponse response =
                new OrganizationResponse(UUID.randomUUID(), "Hospital San Martin");
        when(organizationService.createOrganization(request)).thenReturn(response);

        ResponseEntity<OrganizationResponse> result =
                organizationController.createOrganization(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturnTheCallersOrganization() {
        stubPrincipal();
        MyOrganizationResponse response =
                new MyOrganizationResponse(organizationId, "Hospital", null, null, null, List.of());
        when(organizationService.getMyOrganization(mockUser)).thenReturn(response);

        ResponseEntity<MyOrganizationResponse> result =
                organizationController.getMyOrganization(mockUserDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturn201WhenAddingAdmin() {
        ResponseEntity<Void> result =
                organizationController.addAdmin(
                        organizationId, new AddOrganizationAdminRequest("boss@hospital.com"));

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        verify(organizationService).addAdmin(organizationId, "boss@hospital.com");
    }

    @Test
    void shouldListContractedCourses() {
        stubPrincipal();
        List<OrganizationCourseResponse> response = List.of();
        when(organizationService.getCourses(mockUser, organizationId)).thenReturn(response);

        ResponseEntity<List<OrganizationCourseResponse>> result =
                organizationController.getCourses(organizationId, mockUserDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturn201WhenContractingACourse() {
        UUID courseId = UUID.randomUUID();
        OrganizationCourseResponse response = new OrganizationCourseResponse(null, null);
        when(organizationService.addCourse(organizationId, courseId)).thenReturn(response);

        ResponseEntity<OrganizationCourseResponse> result =
                organizationController.addCourse(
                        organizationId, new AddOrganizationCourseRequest(courseId));

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturn204WhenRemovingAContractedCourse() {
        UUID courseId = UUID.randomUUID();

        ResponseEntity<Void> result = organizationController.removeCourse(organizationId, courseId);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(organizationService).removeCourse(organizationId, courseId);
    }

    @Test
    void shouldReturn201WhenCreatingInviteCode() {
        stubPrincipal();
        CreateInviteCodeRequest request = new CreateInviteCodeRequest(null, null);
        InviteCodeResponse response = inviteCodeResponse();
        when(inviteCodeService.createInviteCode(mockUser, organizationId, request))
                .thenReturn(response);

        ResponseEntity<InviteCodeResponse> result =
                organizationController.createInviteCode(organizationId, request, mockUserDetails);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldListInviteCodes() {
        stubPrincipal();
        List<InviteCodeResponse> response = List.of(inviteCodeResponse());
        when(inviteCodeService.getOrganizationInviteCodes(mockUser, organizationId))
                .thenReturn(response);

        ResponseEntity<List<InviteCodeResponse>> result =
                organizationController.getInviteCodes(organizationId, mockUserDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturn204WhenDeactivatingInviteCode() {
        stubPrincipal();
        UUID inviteCodeId = UUID.randomUUID();

        ResponseEntity<Void> result =
                organizationController.deactivateInviteCode(
                        organizationId, inviteCodeId, mockUserDetails);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        assertNull(result.getBody());
        verify(inviteCodeService).deactivateInviteCode(mockUser, organizationId, inviteCodeId);
    }

    @Test
    void shouldReturn201WhenInvitingByEmail() {
        stubPrincipal();
        InviteByEmailRequest request = new InviteByEmailRequest("ana@hospital.com", null);
        InviteCodeResponse response = inviteCodeResponse();
        when(inviteCodeService.inviteByEmail(mockUser, organizationId, request))
                .thenReturn(response);

        ResponseEntity<InviteCodeResponse> result =
                organizationController.inviteByEmail(organizationId, request, mockUserDetails);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturn201WhenInvitingAnAdminByEmail() {
        stubPrincipal();
        InviteByEmailRequest request = new InviteByEmailRequest("boss@hospital.com", null);
        InviteCodeResponse response = inviteCodeResponse();
        when(inviteCodeService.inviteAdminByEmail(mockUser, organizationId, request))
                .thenReturn(response);

        ResponseEntity<InviteCodeResponse> result =
                organizationController.inviteAdminByEmail(organizationId, request, mockUserDetails);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldRedeemInviteCodeForTheAuthenticatedUser() {
        stubPrincipal();
        RedeemInviteCodeResponse response =
                new RedeemInviteCodeResponse("Hospital", List.of(), false, null, MemberRole.MEMBER);
        when(inviteCodeService.redeem(mockUser, "HSMT2026")).thenReturn(response);

        ResponseEntity<RedeemInviteCodeResponse> result =
                organizationController.redeemInviteCode(
                        new RedeemInviteCodeRequest("HSMT2026"), mockUserDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    private OrganizationDetailResponse detail() {
        return new OrganizationDetailResponse(
                organizationId,
                "Hospital",
                OrganizationStatus.ACTIVE,
                "30123456789",
                "rrhh@hospital.com",
                null,
                50,
                3,
                null);
    }

    @Test
    void shouldReturnOrganizationDetail() {
        stubPrincipal();
        OrganizationDetailResponse detail = detail();
        when(organizationService.getOrganization(mockUser, organizationId)).thenReturn(detail);

        ResponseEntity<OrganizationDetailResponse> result =
                organizationController.getOrganization(organizationId, mockUserDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(detail, result.getBody());
    }

    @Test
    void shouldReturn200WhenUpdatingOrganization() {
        UpdateOrganizationRequest request = new UpdateOrganizationRequest(null, 10);
        OrganizationDetailResponse detail = detail();
        when(organizationService.updateOrganization(organizationId, request)).thenReturn(detail);

        ResponseEntity<OrganizationDetailResponse> result =
                organizationController.updateOrganization(organizationId, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(detail, result.getBody());
    }

    @Test
    void shouldReturn204WhenLeavingOrganization() {
        stubPrincipal();

        ResponseEntity<Void> result = organizationController.leaveOrganization(mockUserDetails);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(memberService).leave(mockUser);
    }

    @Test
    void shouldListOrganizationAdmins() {
        stubPrincipal();
        List<OrganizationAdminResponse> admins =
                List.of(
                        new OrganizationAdminResponse(
                                UUID.randomUUID(), "Ana", "Paz", "ana@hospital.com", null));
        when(organizationService.getAdmins(mockUser, organizationId)).thenReturn(admins);

        ResponseEntity<List<OrganizationAdminResponse>> result =
                organizationController.getAdmins(organizationId, mockUserDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(admins, result.getBody());
    }

    @Test
    void shouldReturn204WhenRemovingAdmin() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<Void> result = organizationController.removeAdmin(organizationId, userId);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(memberService).removeAdmin(organizationId, userId);
    }
}
