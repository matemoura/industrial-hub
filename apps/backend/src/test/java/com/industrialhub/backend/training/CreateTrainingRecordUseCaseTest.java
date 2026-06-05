package com.industrialhub.backend.training;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.qms.ged.application.usecase.GedFileValidator;
import com.industrialhub.backend.qms.ged.domain.InvalidGedFileException;
import com.industrialhub.backend.training.application.dto.TrainingRecordResponse;
import com.industrialhub.backend.training.application.usecase.CreateTrainingRecordUseCase;
import com.industrialhub.backend.training.domain.TrainingCategory;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingCourseNotFoundException;
import com.industrialhub.backend.training.domain.TrainingRecord;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateTrainingRecordUseCaseTest {

    @Mock private TrainingRecordRepository recordRepository;
    @Mock private TrainingCourseRepository courseRepository;
    @Mock private StorageService storageService;
    @Mock private GedFileValidator gedFileValidator;
    @Mock private AuditService auditService;

    private CreateTrainingRecordUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateTrainingRecordUseCase(
            recordRepository, courseRepository, storageService, gedFileValidator, auditService);
    }

    private TrainingCourse buildCourse(Integer validityMonths) {
        return TrainingCourse.builder()
            .id(UUID.randomUUID()).code("GMP-001").title("Curso GMP")
            .category(TrainingCategory.GMP).durationHours(4)
            .validityMonths(validityMonths)
            .active(true).createdAt(LocalDateTime.now()).build();
    }

    private TrainingRecord savedRecord(TrainingCourse course, LocalDate expiresAt) {
        return TrainingRecord.builder()
            .id(UUID.randomUUID()).course(course).username("joao")
            .completedAt(LocalDate.of(2025, 1, 10)).expiresAt(expiresAt)
            .passed(true).recordedBy("supervisor").build();
    }

    @Test
    void shouldCalculateExpiresAtFromValidityMonths() throws IOException {
        TrainingCourse course = buildCourse(12);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        LocalDate completedAt = LocalDate.of(2025, 1, 10);
        LocalDate expectedExpiry = completedAt.plusMonths(12);
        when(recordRepository.save(any())).thenReturn(savedRecord(course, expectedExpiry));

        var req = new CreateTrainingRecordUseCase.Request(
            course.getId(), "joao", completedAt, null, null, true);

        TrainingRecordResponse response = useCase.execute(req, null, "supervisor");

        assertThat(response.expiresAt()).isEqualTo(expectedExpiry);
    }

    @Test
    void shouldLeaveExpiresAtNullWhenNoValidityMonths() throws IOException {
        TrainingCourse course = buildCourse(null);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(recordRepository.save(any())).thenReturn(savedRecord(course, null));

        var req = new CreateTrainingRecordUseCase.Request(
            course.getId(), "joao", LocalDate.now(), null, null, true);

        TrainingRecordResponse response = useCase.execute(req, null, "supervisor");

        assertThat(response.expiresAt()).isNull();
    }

    @Test
    void shouldUploadCertificateWhenFileProvided() throws IOException {
        TrainingCourse course = buildCourse(12);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        MultipartFile cert = mock(MultipartFile.class);
        when(cert.isEmpty()).thenReturn(false);
        when(cert.getInputStream()).thenReturn(new ByteArrayInputStream("PDF".getBytes()));
        when(cert.getContentType()).thenReturn("application/pdf");
        when(cert.getSize()).thenReturn(1024L);
        when(cert.getOriginalFilename()).thenReturn("cert.pdf");

        when(recordRepository.save(any())).thenReturn(
            savedRecord(course, LocalDate.of(2026, 1, 10)));

        var req = new CreateTrainingRecordUseCase.Request(
            course.getId(), "joao", LocalDate.of(2025, 1, 10), null, null, true);

        useCase.execute(req, cert, "supervisor");

        verify(gedFileValidator).validate(cert);
        verify(storageService).upload(anyString(), any(), anyString(), anyLong());
    }

    @Test
    void shouldNotCallStorageWhenNoCertificate() throws IOException {
        TrainingCourse course = buildCourse(null);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(recordRepository.save(any())).thenReturn(savedRecord(course, null));

        var req = new CreateTrainingRecordUseCase.Request(
            course.getId(), "joao", LocalDate.now(), null, null, false);

        useCase.execute(req, null, "supervisor");

        verifyNoInteractions(storageService);
        verifyNoInteractions(gedFileValidator);
    }

    @Test
    void shouldThrowWhenFileValidationFails() throws IOException {
        TrainingCourse course = buildCourse(null);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        MultipartFile badFile = mock(MultipartFile.class);
        when(badFile.isEmpty()).thenReturn(false);
        doThrow(new InvalidGedFileException("MIME inválido")).when(gedFileValidator).validate(badFile);

        var req = new CreateTrainingRecordUseCase.Request(
            course.getId(), "joao", LocalDate.now(), null, null, true);

        assertThatThrownBy(() -> useCase.execute(req, badFile, "supervisor"))
            .isInstanceOf(InvalidGedFileException.class)
            .hasMessageContaining("MIME");
    }

    @Test
    void shouldThrow404WhenCourseNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(courseRepository.findById(unknownId)).thenReturn(Optional.empty());

        var req = new CreateTrainingRecordUseCase.Request(
            unknownId, "joao", LocalDate.now(), null, null, true);

        assertThatThrownBy(() -> useCase.execute(req, null, "supervisor"))
            .isInstanceOf(TrainingCourseNotFoundException.class);
    }
}
