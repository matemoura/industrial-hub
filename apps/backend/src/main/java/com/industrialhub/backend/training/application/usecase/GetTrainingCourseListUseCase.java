package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.training.application.dto.TrainingCourseResponse;
import com.industrialhub.backend.training.infrastructure.TrainingCourseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetTrainingCourseListUseCase {

    private final TrainingCourseRepository courseRepository;

    public GetTrainingCourseListUseCase(TrainingCourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    public Page<TrainingCourseResponse> execute(Pageable pageable) {
        return courseRepository.findAllByOrderByCodeAsc(pageable)
            .map(TrainingCourseResponse::from);
    }
}
