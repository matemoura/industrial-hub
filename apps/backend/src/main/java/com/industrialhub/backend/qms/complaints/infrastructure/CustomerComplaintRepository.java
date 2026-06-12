package com.industrialhub.backend.qms.complaints.infrastructure;

import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.domain.NcSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CustomerComplaintRepository extends JpaRepository<CustomerComplaint, UUID> {

    @Query("""
        SELECT c FROM CustomerComplaint c
        WHERE (:status IS NULL OR c.status = :status)
        AND (:severity IS NULL OR c.severity = :severity)
        AND (:productCode IS NULL OR LOWER(c.productCode) LIKE LOWER(CONCAT('%', CAST(:productCode AS string), '%')))
        AND (:reportedToAnvisa IS NULL OR c.reportedToAnvisa = :reportedToAnvisa)
        AND (:from IS NULL OR c.reportedDate >= :from)
        AND (:to IS NULL OR c.reportedDate <= :to)
        ORDER BY c.reportedDate DESC
        """)
    Page<CustomerComplaint> findByFilters(
        @Param("status") ComplaintStatus status,
        @Param("severity") NcSeverity severity,
        @Param("productCode") String productCode,
        @Param("reportedToAnvisa") Boolean reportedToAnvisa,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        Pageable pageable
    );

    long countByCodeStartingWith(String prefix);

    List<CustomerComplaint> findByReportedDateBetween(LocalDate from, LocalDate to);
}
