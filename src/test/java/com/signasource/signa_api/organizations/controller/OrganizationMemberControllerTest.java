package com.signasource.signa_api.organizations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.MemberProgressResponse;
import com.signasource.signa_api.organizations.dto.OrganizationMemberSummaryResponse;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.service.OrganizationMemberQueryService;
import com.signasource.signa_api.organizations.service.OrganizationMemberService;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberControllerTest {

    @Mock private OrganizationMemberService memberService;
    @Mock private OrganizationMemberQueryService memberQueryService;

    @InjectMocks private OrganizationMemberController memberController;

    private User actor;
    private CustomUserDetails userDetails;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        actor = mock(User.class);
        userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUser()).thenReturn(actor);
        organizationId = UUID.randomUUID();
    }

    @Test
    void shouldReturnThePageOfMembers() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<OrganizationMemberSummaryResponse> page = new PageImpl<>(List.of(), pageable, 0);
        when(memberQueryService.getMembers(
                        actor, organizationId, "ana", MemberStatus.ACTIVE, pageable))
                .thenReturn(page);

        ResponseEntity<Page<OrganizationMemberSummaryResponse>> result =
                memberController.getMembers(
                        organizationId, "ana", MemberStatus.ACTIVE, pageable, userDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(page, result.getBody());
    }

    @Test
    void shouldReturnAMembersProgress() {
        UUID userId = UUID.randomUUID();
        MemberProgressResponse response =
                new MemberProgressResponse(
                        userId, null, null, null, null, null, null, 0, 0, 0, 0, null, 0, 0, 0, 0, 0,
                        0, 0, List.of());
        when(memberQueryService.getMemberProgress(actor, organizationId, userId))
                .thenReturn(response);

        ResponseEntity<MemberProgressResponse> result =
                memberController.getMemberProgress(organizationId, userId, userDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturn204WhenRemovingMember() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<Void> result =
                memberController.removeMember(organizationId, userId, userDetails);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(memberService).removeMember(actor, organizationId, userId);
    }
}
