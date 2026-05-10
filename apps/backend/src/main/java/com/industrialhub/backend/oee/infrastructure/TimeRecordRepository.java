package com.industrialhub.backend.oee.infrastructure;

import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TimeRecordRepository extends JpaRepository<TimeRecord, UUID> {

    List<TimeRecord> findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(
            LocalDate startDate, LocalDate endDate);

    @Query("""
            SELECT t FROM TimeRecord t
            WHERE t.profileDate BETWEEN :start AND :end
            AND (:workerId IS NULL OR t.workerId = :workerId)
            ORDER BY t.profileDate, t.workerId
            """)
    List<TimeRecord> findByPeriodAndOptionalWorker(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("workerId") Long workerId);

    @Query("""
            SELECT t.description as description,
                   COUNT(t) as occurrences,
                   SUM(t.hours) as totalHours
            FROM TimeRecord t
            WHERE t.profileDate BETWEEN :start AND :end
            AND t.recordType = :type
            AND (:workerId IS NULL OR t.workerId = :workerId)
            GROUP BY t.description
            ORDER BY SUM(t.hours) DESC
            """)
    List<IndirectActivitySummary> findActivitySummaryByType(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("workerId") Long workerId,
            @Param("type") RecordType type);
}
