package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.NotificationResponse;
import com.industrialhub.backend.common.application.usecase.GetNotificationsUseCase;
import com.industrialhub.backend.common.application.usecase.GetUnreadCountUseCase;
import com.industrialhub.backend.common.application.usecase.MarkAllNotificationsReadUseCase;
import com.industrialhub.backend.common.application.usecase.MarkNotificationReadUseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final GetNotificationsUseCase getUseCase;
    private final GetUnreadCountUseCase getUnreadCountUseCase;
    private final MarkNotificationReadUseCase markReadUseCase;
    private final MarkAllNotificationsReadUseCase markAllReadUseCase;

    public NotificationController(GetNotificationsUseCase getUseCase,
                                  GetUnreadCountUseCase getUnreadCountUseCase,
                                  MarkNotificationReadUseCase markReadUseCase,
                                  MarkAllNotificationsReadUseCase markAllReadUseCase) {
        this.getUseCase = getUseCase;
        this.getUnreadCountUseCase = getUnreadCountUseCase;
        this.markReadUseCase = markReadUseCase;
        this.markAllReadUseCase = markAllReadUseCase;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @PageableDefault(size = 20) Pageable pageable,
            Principal principal) {
        return ResponseEntity.ok(getUseCase.execute(principal.getName(), pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Principal principal) {
        long count = getUnreadCountUseCase.execute(principal.getName());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable UUID id,
            Principal principal) {
        return ResponseEntity.ok(markReadUseCase.execute(id, principal.getName()));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Principal principal) {
        markAllReadUseCase.execute(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
