package com.justdoit.notification.feature.notification;

import com.justdoit.notification.shared.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @RequestBody @Valid CreateNotificationRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.createNotification(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAll(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(notificationService.getAllByUser(userId));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponse>> getUnread(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(notificationService.getUnreadByUser(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable UUID id,
                                                           @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(notificationService.markAsRead(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> getPreferences(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(notificationService.getOrCreatePreference(userId));
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> updatePreferences(
            @RequestBody NotificationPreferenceRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(notificationService.updatePreference(userId, request));
    }
}
