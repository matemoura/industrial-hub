package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.SupplierQualityScore;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.Supplier;
import com.industrialhub.backend.qms.domain.SupplierNotFoundException;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GetSupplierQualityUseCase {

    private final SupplierRepository supplierRepository;
    private final NonConformanceRepository ncRepository;

    public GetSupplierQualityUseCase(SupplierRepository supplierRepository,
                                     NonConformanceRepository ncRepository) {
        this.supplierRepository = supplierRepository;
        this.ncRepository = ncRepository;
    }

    @Transactional(readOnly = true)
    public SupplierQualityScore executeForSupplier(UUID supplierId, int days) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierNotFoundException(supplierId));

        LocalDateTime from = LocalDateTime.now().minusDays(days);
        List<NonConformance> ncs = ncRepository.findBySupplierIdAndReportedAtAfter(supplierId, from);

        return computeScore(supplier, ncs);
    }

    @Transactional(readOnly = true)
    public List<SupplierQualityScore> executeRanking(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        List<Supplier> suppliers = supplierRepository.findAllByActiveTrue();
        List<NonConformance> allNcs = ncRepository.findByTypeAndReportedAtAfter(NcType.SUPPLIER, from);

        return suppliers.stream()
                .map(supplier -> {
                    List<NonConformance> supplierNcs = allNcs.stream()
                            .filter(nc -> nc.getSupplier() != null
                                    && supplier.getId().equals(nc.getSupplier().getId()))
                            .toList();
                    return computeScore(supplier, supplierNcs);
                })
                .sorted(Comparator.comparingDouble(SupplierQualityScore::qualityScore))
                .toList();
    }

    private SupplierQualityScore computeScore(Supplier supplier, List<NonConformance> ncs) {
        long total = ncs.size();
        long critical = ncs.stream().filter(nc -> nc.getSeverity() == NcSeverity.CRITICAL).count();
        long high = ncs.stream().filter(nc -> nc.getSeverity() == NcSeverity.HIGH).count();
        long medium = ncs.stream().filter(nc -> nc.getSeverity() == NcSeverity.MEDIUM).count();
        long low = ncs.stream().filter(nc -> nc.getSeverity() == NcSeverity.LOW).count();

        double penalty = (critical * 5.0 + high * 2.0 + medium * 1.0 + low * 0.5) / Math.max(total, 1) * 100;
        double score = Math.max(0, Math.min(100, 100 - penalty));

        return new SupplierQualityScore(supplier.getId(), supplier.getName(), total, critical, high, score);
    }
}
