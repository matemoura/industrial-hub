package com.industrialhub.backend.oee.infrastructure;

import com.industrialhub.backend.oee.domain.ImportBatch;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TimeRecordRepository extends JpaRepository<TimeRecord, UUID> {

    @Query("""
            SELECT DISTINCT t.workerId AS workerId, t.workerName AS workerName
            FROM TimeRecord t
            ORDER BY t.workerName ASC
            """)
    List<WorkerProjection> findDistinctWorkers();

    @Modifying
    @Query("DELETE FROM TimeRecord t WHERE t.batch = :batch")
    void deleteAllByBatch(@Param("batch") ImportBatch batch);

    List<TimeRecord> findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(
            LocalDate startDate, LocalDate endDate);

    @Query("""
            SELECT t FROM TimeRecord t
            WHERE t.profileDate BETWEEN :startDate AND :endDate
              AND t.batch.shift.id = :shiftId
            ORDER BY t.workerId ASC, t.profileDate ASC
            """)
    List<TimeRecord> findByProfileDateBetweenAndShiftIdOrderByWorkerIdAscProfileDateAsc(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("shiftId") UUID shiftId);

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
            SELECT t.description AS description,
                   SUM(t.hours) AS totalHours,
                   COUNT(DISTINCT t.workerId) AS workerCount,
                   COUNT(t) AS occurrences
            FROM TimeRecord t
            WHERE t.profileDate BETWEEN :start AND :end
            AND t.recordType = :type
            AND (:workerId IS NULL OR t.workerId = :workerId)
            GROUP BY t.description
            ORDER BY SUM(t.hours) DESC
            """)
    List<ProcessSummary> findProcessSummary(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("workerId") Long workerId,
            @Param("type") RecordType type);

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
