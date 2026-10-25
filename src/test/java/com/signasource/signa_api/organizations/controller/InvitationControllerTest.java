package com.signasource.signa_api.organizations.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.AcceptInvitationResponse;
import com.signasource.signa_api.organizations.dto.InvitationPreviewResponse;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.service.SeatInvitationService;
import com.signasource.signa_api.users.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationControllerTest {

    @Mock private SeatInvitationService invitationService;

    @InjectMocks private InvitationController controller;

    @Test
    void shouldPreviewAnInvitation() {
        InvitationPreviewResponse preview =
                new InvitationPreviewResponse(
                        "Hospital", "LSA", "a@x.com", SeatStatus.INVITED, null, 30, false);
        when(invitationService.preview("tok")).thenReturn(preview);

        assertSame(preview, controller.preview("tok").getBody());
    }

    @Test
    void shouldAcceptForTheAuthenticatedUser() {
        User user = mock(User.class);
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getUser()).thenReturn(user);
        AcceptInvitationResponse accepted =
                new AcceptInvitationResponse("Hospital", null, "LSA", null, null);
        when(invitationService.accept(user, "tok")).thenReturn(accepted);

        assertSame(accepted, controller.accept("tok", principal).getBody());
    }
}
