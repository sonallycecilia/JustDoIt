package com.justdoit.notification.feature.support;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/support/messages")
@RequiredArgsConstructor
public class SupportMessageController {

    private final SupportMessageService service;

    @GetMapping
    public ResponseEntity<List<SupportMessageResponse>> getConversation(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(service.getConversation(userId));
    }

    @PostMapping
    public ResponseEntity<SupportMessageResponse> send(
            @RequestBody @Valid SupportMessageRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(request, userId));
    }
}
