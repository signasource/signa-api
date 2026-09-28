package com.signasource.signa_api.organizations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.service.OrganizationMemberService;
import com.signasource.signa_api.users.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberControllerTest {

    @Mock private OrganizationMemberService memberService;

    @InjectMocks private OrganizationMemberController memberController;

    @Test
    void shouldReturn204WhenRemovingMember() {
        User actor = mock(User.class);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUser()).thenReturn(actor);
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ResponseEntity<Void> result =
                memberController.removeMember(organizationId, userId, userDetails);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(memberService).removeMember(actor, organizationId, userId);
    }
}
