package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CreateSignReportRequest;
import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignReport;
import com.signasource.signa_api.learning.repository.SignReportRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignReportService {

    private final SignReportRepository signReportRepository;
    private final SignRepository signRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createReport(CreateSignReportRequest request, UUID userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        Sign sign =
                signRepository
                        .findById(request.signId())
                        .orElseThrow(() -> new NotFoundException("Seña no encontrada"));

        SignReport report = new SignReport();
        report.setUser(user);
        report.setSign(sign);
        report.setReason(request.reason());
        report.setDescription(request.description());
        report.setStatus(ReportStatus.PENDING);

        signReportRepository.save(report);
    }
}
