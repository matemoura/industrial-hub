package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.training.application.dto.CompetencyMatrixRow;
import com.industrialhub.backend.training.domain.CompetencyStatus;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingRecord;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GetCompetencyMatrixUseCase {

    private final UserRepository userRepository;
    private final TrainingCourseRepository courseRepository;
    private final TrainingRecordRepository recordRepository;

    public GetCompetencyMatrixUseCase(UserRepository userRepository,
                                      TrainingCourseRepository courseRepository,
                                      TrainingRecordRepository recordRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.recordRepository = recordRepository;
    }

    public List<CompetencyMatrixRow> execute() {
        List<User> users = userRepository.findAllByOrderByUsernameAsc();
        List<TrainingCourse> allActiveCourses = courseRepository.findByActiveTrue();

        List<UUID> allCourseIds = allActiveCourses.stream().map(TrainingCourse::getId).toList();
        List<TrainingRecord> allRecords = allCourseIds.isEmpty()
            ? List.of()
            : recordRepository.findByCourseIdIn(allCourseIds);

        Map<String, Map<UUID, TrainingRecord>> recordsByUserAndCourse = allRecords.stream()
            .collect(Collectors.groupingBy(
                TrainingRecord::getUsername,
                Collectors.toMap(r -> r.getCourse().getId(), r -> r, (a, b) ->
                    a.getCompletedAt().isAfter(b.getCompletedAt()) ? a : b)
            ));

        List<CompetencyMatrixRow> rows = new ArrayList<>();
        for (User user : users) {
            String roleName = user.getRole().name();
            List<TrainingCourse> required = allActiveCourses.stream()
                .filter(c -> c.getRequiredForRoles().contains(roleName))
                .toList();

            Map<UUID, TrainingRecord> userRecords =
                recordsByUserAndCourse.getOrDefault(user.getUsername(), Map.of());

            for (TrainingCourse course : required) {
                TrainingRecord record = userRecords.get(course.getId());
                CompetencyStatus status = computeStatus(record);

                rows.add(new CompetencyMatrixRow(
                    user.getUsername(),
                    roleName,
                    course.getId(),
                    course.getCode(),
                    course.getTitle(),
                    status,
                    record != null ? record.getCompletedAt() : null,
                    record != null ? record.getExpiresAt() : null
                ));
            }
        }

        return rows;
    }

    private CompetencyStatus computeStatus(TrainingRecord record) {
        if (record == null || !record.isPassed()) return CompetencyStatus.MISSING;
        if (record.getExpiresAt() == null) return CompetencyStatus.VALID;
        LocalDate today = LocalDate.now();
        if (record.getExpiresAt().isBefore(today)) return CompetencyStatus.EXPIRED;
        if (!record.getExpiresAt().isAfter(today.plusDays(30))) return CompetencyStatus.EXPIRING;
        return CompetencyStatus.VALID;
    }
}
