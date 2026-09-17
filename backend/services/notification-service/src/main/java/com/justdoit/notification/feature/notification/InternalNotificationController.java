package com.justdoit.notification.feature.notification;

import com.justdoit.notification.shared.InternalNotificationRequest;
import com.justdoit.notification.shared.NotificationResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private final NotificationService notificationService;
    private final byte[] internalToken;

    public InternalNotificationController(NotificationService notificationService,
            @Value("${app.internal-token:}") String internalToken) {
        this.notificationService = notificationService;
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> create(
            @RequestHeader(value = "X-Internal-Token", required = false) String providedToken,
            @RequestBody @Valid InternalNotificationRequest request) {
        if (internalToken.length == 0 || providedToken == null
                || !MessageDigest.isEqual(internalToken,
                    providedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.createInternalNotification(request));
    }
}
