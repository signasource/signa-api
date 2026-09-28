package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.organizations.dto.CreateOrganizationRequest;
import com.signasource.signa_api.organizations.dto.OrganizationResponse;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks private OrganizationService organizationService;

    @Test
    void createOrganization_SavesAndReturnsIt() {
        CreateOrganizationRequest request = new CreateOrganizationRequest("Hospital San Martín");
        when(organizationRepository.existsByNameIgnoreCase("Hospital San Martín")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(
                        i -> {
                            Organization org = i.getArgument(0);
                            org.setId(UUID.randomUUID());
                            return org;
                        });

        OrganizationResponse response = organizationService.createOrganization(request);

        assertEquals("Hospital San Martín", response.name());
    }

    @Test
    void createOrganization_ThrowsConflict_WhenNameAlreadyExists() {
        CreateOrganizationRequest request = new CreateOrganizationRequest("Hospital San Martín");
        when(organizationRepository.existsByNameIgnoreCase("Hospital San Martín")).thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> organizationService.createOrganization(request));
        verify(organizationRepository, never()).save(any());
    }
}
