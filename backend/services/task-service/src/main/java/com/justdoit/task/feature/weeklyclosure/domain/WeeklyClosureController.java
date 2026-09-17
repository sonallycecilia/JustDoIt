package com.justdoit.task.feature.weeklyclosure.domain;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/weekly-cycles/current")
public class WeeklyClosureController {

    private final WeeklyClosurePreviewService previewService;
    private final WeeklyClosureService closureService;

    public WeeklyClosureController(WeeklyClosurePreviewService previewService, WeeklyClosureService closureService) {
        this.previewService = previewService;
        this.closureService = closureService;
    }

    @GetMapping("/closure-preview")
    public ResponseEntity<ClosurePreviewDTO> getClosurePreview(
            @RequestAttribute("userId") UUID userId) { 
        
        ClosurePreviewDTO preview = previewService.getClosurePreview(userId);
        return ResponseEntity.ok(preview);
    }

    @PostMapping("/closure")
    public ResponseEntity<Void> executeClosure(
            @RequestBody ClosureCommandDTO command,
            @RequestAttribute("userId") UUID userId) { 
        
        // Proteção de Segurança: Garantimos que o userId que vai operar a transação
        // é o do usuário autenticado no token, ignorando o userId que possa vir no body.
        ClosureCommandDTO secureCommand = new ClosureCommandDTO(
                command.cycleId(),
                userId,
                command.tasksToMigrate(),
                command.tasksToArchive()
        );

        closureService.executeClosure(secureCommand);
        return ResponseEntity.noContent().build();
    }
}