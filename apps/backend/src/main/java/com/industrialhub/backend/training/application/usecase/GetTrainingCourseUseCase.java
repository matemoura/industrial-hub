package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.training.application.dto.TrainingCourseResponse;
import com.industrialhub.backend.training.domain.TrainingCourseNotFoundException;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetTrainingCourseUseCase {

    private final TrainingCourseRepository courseRepository;

    public GetTrainingCourseUseCase(TrainingCourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    public TrainingCourseResponse execute(UUID id) {
        return courseRepository.findById(id)
            .map(TrainingCourseResponse::from)
            .orElseThrow(() -> new TrainingCourseNotFoundException(id));
    }
}
