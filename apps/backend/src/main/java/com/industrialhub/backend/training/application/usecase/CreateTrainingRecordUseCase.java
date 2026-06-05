package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.qms.ged.application.usecase.GedFileValidator;
import com.industrialhub.backend.training.application.dto.TrainingRecordResponse;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingCourseNotFoundException;
import com.industrialhub.backend.training.domain.TrainingRecord;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class CreateTrainingRecordUseCase {

    private final TrainingRecordRepository recordRepository;
    private final TrainingCourseRepository courseRepository;
    private final StorageService storageService;
    private final GedFileValidator gedFileValidator;
    private final AuditService auditService;

    public CreateTrainingRecordUseCase(TrainingRecordRepository recordRepository,
                                       TrainingCourseRepository courseRepository,
                                       StorageService storageService,
                                       GedFileValidator gedFileValidator,
                                       AuditService auditService) {
        this.recordRepository = recordRepository;
        this.courseRepository = courseRepository;
        this.storageService = storageService;
        this.gedFileValidator = gedFileValidator;
        this.auditService = auditService;
    }

    public record Request(
        @NotNull UUID courseId,
        @NotBlank String username,
        @NotNull LocalDate completedAt,
        String instructorName,
        @Min(0) @Max(100) Integer score,
        @NotNull Boolean passed
    ) {}

    @Transactional
    public TrainingRecordResponse execute(Request req, MultipartFile certificate, String principal)
            throws IOException {

        TrainingCourse course = courseRepository.findById(req.courseId())
            .orElseThrow(() -> new TrainingCourseNotFoundException(req.courseId()));

        String storagePath = null;
        if (certificate != null && !certificate.isEmpty()) {
            gedFileValidator.validate(certificate);
            String key = "training/%s/%s_%s".formatted(
                req.username(),
                UUID.randomUUID(),
                GedFileValidator.sanitizeFilename(certificate.getOriginalFilename())
            );
            storageService.upload(key, certificate.getInputStream(),
                certificate.getContentType(), certificate.getSize());
            storagePath = key;
        }

        LocalDate expiresAt = course.getValidityMonths() != null
            ? req.completedAt().plusMonths(course.getValidityMonths())
            : null;

        TrainingRecord record = TrainingRecord.builder()
            .course(course)
            .username(req.username())
            .completedAt(req.completedAt())
            .expiresAt(expiresAt)
            .instructorName(req.instructorName())
            .score(req.score())
            .passed(req.passed())
            .certificateStoragePath(storagePath)
            .recordedBy(principal)
            .build();

        record = recordRepository.save(record);

        auditService.log(principal, AuditAction.TRAINING_RECORD_CREATED, "TrainingRecord",
            record.getId(), Map.of("courseCode", course.getCode(), "username", req.username()));

        return TrainingRecordResponse.from(record);
    }
}
