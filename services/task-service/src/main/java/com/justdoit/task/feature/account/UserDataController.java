package com.justdoit.task.feature.account;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Remoção dos dados do usuário neste serviço (tarefas e categorias).
 * Chamado pelo auth-service durante a exclusão de conta, repassando o token do
 * próprio usuário — por isso a autenticação JWT padrão já protege o endpoint.
 */
@RestController
@RequestMapping("/me/data")
@RequiredArgsConstructor
public class UserDataController {

    private final UserDataService userDataService;

    @DeleteMapping
    public ResponseEntity<Void> deleteMyData(@AuthenticationPrincipal UUID userId) {
        userDataService.deleteAllForUser(userId);
        return ResponseEntity.noContent().build();
    }
}
