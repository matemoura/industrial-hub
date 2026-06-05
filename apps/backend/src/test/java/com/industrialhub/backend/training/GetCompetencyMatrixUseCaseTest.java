package com.industrialhub.backend.training;

import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.training.application.dto.CompetencyMatrixRow;
import com.industrialhub.backend.training.application.usecase.GetCompetencyMatrixUseCase;
import com.industrialhub.backend.training.domain.CompetencyStatus;
import com.industrialhub.backend.training.domain.TrainingCategory;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingRecord;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCompetencyMatrixUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private TrainingCourseRepository courseRepository;
    @Mock private TrainingRecordRepository recordRepository;

    private GetCompetencyMatrixUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCompetencyMatrixUseCase(userRepository, courseRepository, recordRepository);
    }

    private User buildUser(String username, Role role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setRole(role);
        u.setActive(true);
        return u;
    }

    private TrainingCourse buildCourse(String code, String role, Integer validityMonths) {
        return TrainingCourse.builder()
            .id(UUID.randomUUID()).code(code).title("Curso " + code)
            .category(TrainingCategory.GMP).durationHours(4)
            .validityMonths(validityMonths)
            .requiredForRoles(Set.of(role))
            .active(true).createdAt(LocalDateTime.now()).build();
    }

    @Test
    void shouldReturnMissingWhenNoRecord() {
        User user = buildUser("joao", Role.OPERATOR);
        TrainingCourse course = buildCourse("GMP-001", "OPERATOR", 12);

        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(user));
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(course));
        when(recordRepository.findByCourseIdIn(any())).thenReturn(List.of());

        List<CompetencyMatrixRow> rows = useCase.execute();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo(CompetencyStatus.MISSING);
        assertThat(rows.get(0).username()).isEqualTo("joao");
    }

    @Test
    void shouldReturnValidWhenRecordPassedAndNotExpired() {
        User user = buildUser("maria", Role.OPERATOR);
        TrainingCourse course = buildCourse("GMP-001", "OPERATOR", 24);
        UUID courseId = course.getId();

        TrainingRecord record = TrainingRecord.builder()
            .id(UUID.randomUUID()).course(course).username("maria")
            .completedAt(LocalDate.now().minusMonths(6))
            .expiresAt(LocalDate.now().plusMonths(18))
            .passed(true).recordedBy("admin").build();

        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(user));
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(course));
        when(recordRepository.findByCourseIdIn(List.of(courseId))).thenReturn(List.of(record));

        List<CompetencyMatrixRow> rows = useCase.execute();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo(CompetencyStatus.VALID);
    }

    @Test
    void shouldReturnExpiringWhenWithin30Days() {
        User user = buildUser("pedro", Role.OPERATOR);
        TrainingCourse course = buildCourse("GMP-002", "OPERATOR", 12);
        UUID courseId = course.getId();

        TrainingRecord record = TrainingRecord.builder()
            .id(UUID.randomUUID()).course(course).username("pedro")
            .completedAt(LocalDate.now().minusMonths(11))
            .expiresAt(LocalDate.now().plusDays(15))
            .passed(true).recordedBy("admin").build();

        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(user));
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(course));
        when(recordRepository.findByCourseIdIn(List.of(courseId))).thenReturn(List.of(record));

        List<CompetencyMatrixRow> rows = useCase.execute();

        assertThat(rows.get(0).status()).isEqualTo(CompetencyStatus.EXPIRING);
    }

    @Test
    void shouldReturnExpiredWhenPastExpiresAt() {
        User user = buildUser("ana", Role.OPERATOR);
        TrainingCourse course = buildCourse("GMP-003", "OPERATOR", 12);
        UUID courseId = course.getId();

        TrainingRecord record = TrainingRecord.builder()
            .id(UUID.randomUUID()).course(course).username("ana")
            .completedAt(LocalDate.now().minusMonths(14))
            .expiresAt(LocalDate.now().minusDays(30))
            .passed(true).recordedBy("admin").build();

        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(user));
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(course));
        when(recordRepository.findByCourseIdIn(List.of(courseId))).thenReturn(List.of(record));

        List<CompetencyMatrixRow> rows = useCase.execute();

        assertThat(rows.get(0).status()).isEqualTo(CompetencyStatus.EXPIRED);
    }

    @Test
    void shouldReturnMissingWhenRecordNotPassed() {
        User user = buildUser("luis", Role.OPERATOR);
        TrainingCourse course = buildCourse("GMP-004", "OPERATOR", null);
        UUID courseId = course.getId();

        TrainingRecord record = TrainingRecord.builder()
            .id(UUID.randomUUID()).course(course).username("luis")
            .completedAt(LocalDate.now().minusDays(5))
            .passed(false).recordedBy("admin").build();

        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(user));
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(course));
        when(recordRepository.findByCourseIdIn(List.of(courseId))).thenReturn(List.of(record));

        List<CompetencyMatrixRow> rows = useCase.execute();

        assertThat(rows.get(0).status()).isEqualTo(CompetencyStatus.MISSING);
    }

    @Test
    void shouldReturnValidWhenNoExpiry() {
        User user = buildUser("carla", Role.OPERATOR);
        TrainingCourse course = buildCourse("GMP-005", "OPERATOR", null);
        UUID courseId = course.getId();

        TrainingRecord record = TrainingRecord.builder()
            .id(UUID.randomUUID()).course(course).username("carla")
            .completedAt(LocalDate.now().minusYears(5))
            .expiresAt(null).passed(true).recordedBy("admin").build();

        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(user));
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(course));
        when(recordRepository.findByCourseIdIn(List.of(courseId))).thenReturn(List.of(record));

        List<CompetencyMatrixRow> rows = useCase.execute();

        assertThat(rows.get(0).status()).isEqualTo(CompetencyStatus.VALID);
    }

    @Test
    void shouldIgnoreCoursesNotRequiredForUserRole() {
        User user = buildUser("oper1", Role.OPERATOR);
        TrainingCourse adminCourse = buildCourse("ADM-001", "ADMIN", 12);
        TrainingCourse operCourse = buildCourse("OPR-001", "OPERATOR", 12);
        UUID operCourseId = operCourse.getId();

        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(user));
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(adminCourse, operCourse));
        when(recordRepository.findByCourseIdIn(any())).thenReturn(List.of());

        List<CompetencyMatrixRow> rows = useCase.execute();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).courseCode()).isEqualTo("OPR-001");
    }
}
