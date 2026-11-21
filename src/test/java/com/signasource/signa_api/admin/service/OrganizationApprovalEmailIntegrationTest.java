package com.signasource.signa_api.admin.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.admin.dto.UpdateOrganizationStatusRequest;
import com.signasource.signa_api.auth.service.EmailService;
import com.signasource.signa_api.organizations.dto.RegisterOrganizationRequest;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.service.OrganizationService;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Over the real registration and the real transaction (H2): validating an organization emails the
 * address it registered with, only once the status change commits.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrganizationApprovalEmailIntegrationTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private EmailService emailService;

    @Autowired private OrganizationService organizationService;
    @Autowired private AdminService adminService;
    @Autowired private PlatformTransactionManager transactionManager;

    private UUID orgId;
    private String orgName;
    private String contactEmail;

    @BeforeEach
    void registerOrganization() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        orgName = "Org " + suffix;
        contactEmail = "contacto-" + suffix + "@example.com";
        String cuit = String.format("30%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
        orgId =
                organizationService
                        .registerOrganization(
                                new RegisterOrganizationRequest(
                                        orgName,
                                        cuit,
                                        // Registration normalizes the email; the mail goes there.
                                        "  " + contactEmail.toUpperCase() + " ",
                                        "+54 11 1234-5678",
                                        "Ana",
                                        "Password1!"))
                        .organizationId();
    }

    @Test
    void validatingEmailsTheRegistrationAddressAfterCommit() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            adminService.updateOrganizationStatus(orgId, approve());
                            // Still inside the transaction: nothing may go out yet.
                            verifyNoInteractions(emailService);
                        });

        verify(emailService).sendOrganizationApprovedEmail(contactEmail, orgName);
    }

    @Test
    void rolledBackValidationSendsNothing() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            adminService.updateOrganizationStatus(orgId, approve());
                            status.setRollbackOnly();
                        });

        verify(emailService, never()).sendOrganizationApprovedEmail(any(), any());
    }

    @Test
    void reactivatingASuspendedOrganizationSendsNothing() {
        adminService.updateOrganizationStatus(orgId, approve());
        adminService.updateOrganizationStatus(
                orgId,
                new UpdateOrganizationStatusRequest(
                        OrganizationStatus.SUSPENDED, "Pago pendiente"));

        adminService.updateOrganizationStatus(orgId, approve());

        verify(emailService).sendOrganizationApprovedEmail(contactEmail, orgName);
    }

    @Test
    void rejectingSendsNothing() {
        adminService.updateOrganizationStatus(
                orgId, new UpdateOrganizationStatusRequest(OrganizationStatus.REJECTED));

        verify(emailService, never()).sendOrganizationApprovedEmail(any(), any());
    }

    private static UpdateOrganizationStatusRequest approve() {
        return new UpdateOrganizationStatusRequest(OrganizationStatus.ACTIVE);
    }
}
