package com.industrialhub.backend.maintenance.presentation;

import com.industrialhub.backend.maintenance.application.dto.CreateEquipmentRequest;
import com.industrialhub.backend.maintenance.application.dto.CreateScheduleRequest;
import com.industrialhub.backend.maintenance.application.dto.CreateWorkOrderRequest;
import com.industrialhub.backend.maintenance.application.dto.EquipmentResponse;
import com.industrialhub.backend.maintenance.application.dto.ScheduleResponse;
import com.industrialhub.backend.maintenance.application.dto.TransitionWorkOrderStatusRequest;
import com.industrialhub.backend.maintenance.application.dto.UpdateEquipmentRequest;
import com.industrialhub.backend.maintenance.application.dto.UpdateScheduleRequest;
import com.industrialhub.backend.maintenance.application.dto.WorkOrderMetricsResponse;
import com.industrialhub.backend.maintenance.application.dto.WorkOrderResponse;
import com.industrialhub.backend.maintenance.application.usecase.CreateEquipmentUseCase;
import com.industrialhub.backend.maintenance.application.usecase.CreateScheduleUseCase;
import com.industrialhub.backend.maintenance.application.usecase.CreateWorkOrderUseCase;
import com.industrialhub.backend.maintenance.application.usecase.DeactivateScheduleUseCase;
import com.industrialhub.backend.maintenance.application.usecase.DeleteEquipmentUseCase;
import com.industrialhub.backend.maintenance.application.usecase.GetEquipmentDetailUseCase;
import com.industrialhub.backend.maintenance.application.usecase.GetEquipmentListUseCase;
import com.industrialhub.backend.maintenance.application.usecase.GetScheduleDetailUseCase;
import com.industrialhub.backend.maintenance.application.usecase.GetScheduleListUseCase;
import com.industrialhub.backend.maintenance.application.usecase.GetWorkOrderListUseCase;
import com.industrialhub.backend.maintenance.application.usecase.GetWorkOrderMetricsUseCase;
import com.industrialhub.backend.maintenance.application.usecase.TransitionWorkOrderStatusUseCase;
import com.industrialhub.backend.maintenance.application.usecase.UpdateEquipmentUseCase;
import com.industrialhub.backend.maintenance.application.usecase.UpdateScheduleUseCase;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {

    private final CreateEquipmentUseCase createEquipment;
    private final GetEquipmentListUseCase getEquipmentList;
    private final GetEquipmentDetailUseCase getEquipmentDetail;
    private final UpdateEquipmentUseCase updateEquipment;
    private final DeleteEquipmentUseCase deleteEquipment;
    private final CreateWorkOrderUseCase createWorkOrder;
    private final GetWorkOrderListUseCase getWorkOrderList;
    private final GetWorkOrderMetricsUseCase getWorkOrderMetrics;
    private final TransitionWorkOrderStatusUseCase transitionWorkOrderStatus;
    private final CreateScheduleUseCase createSchedule;
    private final GetScheduleListUseCase getScheduleList;
    private final GetScheduleDetailUseCase getScheduleDetail;
    private final UpdateScheduleUseCase updateSchedule;
    private final DeactivateScheduleUseCase deactivateSchedule;

    public MaintenanceController(CreateEquipmentUseCase createEquipment,
                                  GetEquipmentListUseCase getEquipmentList,
                                  GetEquipmentDetailUseCase getEquipmentDetail,
                                  UpdateEquipmentUseCase updateEquipment,
                                  DeleteEquipmentUseCase deleteEquipment,
                                  CreateWorkOrderUseCase createWorkOrder,
                                  GetWorkOrderListUseCase getWorkOrderList,
                                  GetWorkOrderMetricsUseCase getWorkOrderMetrics,
                                  TransitionWorkOrderStatusUseCase transitionWorkOrderStatus,
                                  CreateScheduleUseCase createSchedule,
                                  GetScheduleListUseCase getScheduleList,
                                  GetScheduleDetailUseCase getScheduleDetail,
                                  UpdateScheduleUseCase updateSchedule,
                                  DeactivateScheduleUseCase deactivateSchedule) {
        this.createEquipment = createEquipment;
        this.getEquipmentList = getEquipmentList;
        this.getEquipmentDetail = getEquipmentDetail;
        this.updateEquipment = updateEquipment;
        this.deleteEquipment = deleteEquipment;
        this.createWorkOrder = createWorkOrder;
        this.getWorkOrderList = getWorkOrderList;
        this.getWorkOrderMetrics = getWorkOrderMetrics;
        this.transitionWorkOrderStatus = transitionWorkOrderStatus;
        this.createSchedule = createSchedule;
        this.getScheduleList = getScheduleList;
        this.getScheduleDetail = getScheduleDetail;
        this.updateSchedule = updateSchedule;
        this.deactivateSchedule = deactivateSchedule;
    }

    // --- Equipment endpoints ---

    @PostMapping("/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentResponse createEquipment(@Valid @RequestBody CreateEquipmentRequest request,
                                              Principal principal) {
        return createEquipment.execute(request, principal.getName());
    }

    @GetMapping("/equipment")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public List<EquipmentResponse> listEquipment(
            @RequestParam(required = false) EquipmentType type,
            @RequestParam(required = false) EquipmentStatus status) {
        return getEquipmentList.execute(type, status);
    }

    @GetMapping("/equipment/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public EquipmentResponse getEquipment(@PathVariable UUID id) {
        return getEquipmentDetail.execute(id);
    }

    @PutMapping("/equipment/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentResponse updateEquipment(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateEquipmentRequest request) {
        return updateEquipment.execute(id, request);
    }

    @DeleteMapping("/equipment/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteEquipment(@PathVariable UUID id, Principal principal) {
        deleteEquipment.execute(id, principal.getName());
    }

    // --- Work order endpoints ---

    @PostMapping("/work-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public WorkOrderResponse createWorkOrder(@Valid @RequestBody CreateWorkOrderRequest request,
                                              Principal principal) {
        return createWorkOrder.execute(request, principal.getName());
    }

    @GetMapping("/work-orders")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public Page<WorkOrderResponse> listWorkOrders(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) WorkOrderType type,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) WorkOrderPriority priority,
            @PageableDefault(size = 20, sort = "openedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return getWorkOrderList.execute(equipmentId, type, status, priority, pageable);
    }

    @GetMapping("/work-orders/metrics")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public WorkOrderMetricsResponse getMetrics(@RequestParam(required = false) UUID equipmentId) {
        return getWorkOrderMetrics.execute(equipmentId);
    }

    @PutMapping("/work-orders/{id}/status")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public WorkOrderResponse transitionStatus(@PathVariable UUID id,
                                               @Valid @RequestBody TransitionWorkOrderStatusRequest request,
                                               Principal principal) {
        return transitionWorkOrderStatus.execute(id, request.status(), principal.getName());
    }

    // --- Schedule endpoints ---

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ScheduleResponse createSchedule(@Valid @RequestBody CreateScheduleRequest request,
                                            Principal principal) {
        return createSchedule.execute(request, principal.getName());
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public List<ScheduleResponse> listSchedules(@RequestParam(required = false) UUID equipmentId) {
        return getScheduleList.execute(equipmentId);
    }

    @GetMapping("/schedules/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public ScheduleResponse getSchedule(@PathVariable UUID id) {
        return getScheduleDetail.execute(id);
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ScheduleResponse updateSchedule(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateScheduleRequest request,
                                            Principal principal) {
        return updateSchedule.execute(id, request, principal.getName());
    }

    @PutMapping("/schedules/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public void deactivateSchedule(@PathVariable UUID id, Principal principal) {
        deactivateSchedule.execute(id, principal.getName());
    }
}
