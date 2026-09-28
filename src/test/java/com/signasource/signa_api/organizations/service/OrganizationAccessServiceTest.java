package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.ForbiddenException;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationAccessServiceTest {

    @Mock private OrganizationMemberRepository memberRepository;

    @InjectMocks private OrganizationAccessService accessService;

    private final UUID organizationId = UUID.randomUUID();

    private User userWithRole(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }

    @Test
    void shouldAllowSignaAdminWithoutMembership() {
        User admin = userWithRole(Role.ADMIN);

        assertDoesNotThrow(() -> accessService.requireManage(admin, organizationId));
        verifyNoInteractions(memberRepository);
    }

    @Test
    void shouldAllowActiveAdminOfTheSameOrganization() {
        User orgAdmin = userWithRole(Role.ORG_ADMIN);
        when(memberRepository.existsByOrganizationIdAndUserIdAndRoleAndStatus(
                        organizationId, orgAdmin.getId(), MemberRole.ADMIN, MemberStatus.ACTIVE))
                .thenReturn(true);

        assertDoesNotThrow(() -> accessService.requireManage(orgAdmin, organizationId));
    }

    @Test
    void shouldRejectOrgAdminOfAnotherOrganization() {
        User orgAdmin = userWithRole(Role.ORG_ADMIN);
        when(memberRepository.existsByOrganizationIdAndUserIdAndRoleAndStatus(
                        organizationId, orgAdmin.getId(), MemberRole.ADMIN, MemberStatus.ACTIVE))
                .thenReturn(false);

        assertThrows(
                ForbiddenException.class,
                () -> accessService.requireManage(orgAdmin, organizationId));
    }

    @Test
    void shouldRejectRegularUsers() {
        User regular = userWithRole(Role.USER);

        assertThrows(
                ForbiddenException.class,
                () -> accessService.requireManage(regular, organizationId));
        verifyNoInteractions(memberRepository);
    }
}
