package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    List<Shift> findAllByActiveTrueOrderByStartTime();
}
