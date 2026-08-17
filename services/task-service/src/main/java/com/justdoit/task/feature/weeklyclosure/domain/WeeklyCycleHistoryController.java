// src/main/java/com/justdoit/task/feature/weeklyclosure/domain/WeeklyCycleHistoryController.java
package com.justdoit.task.feature.weeklyclosure.domain;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/weekly-cycles")
public class WeeklyCycleHistoryController {

    private final WeeklyCycleHistoryService historyService;

    public WeeklyCycleHistoryController(WeeklyCycleHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<List<WeeklyCycle>> getCycles(
            @RequestParam(required = false) CycleStatus status,
            @RequestAttribute("userId") UUID userId) {
        
        if (status == CycleStatus.CLOSED) {
            List<WeeklyCycle> closedCycles = historyService.getClosedCycles(userId);
            return ResponseEntity.ok(closedCycles);
        }
        
        // Pode ser expandido futuramente para buscar todos, mas agora atende a query de status=CLOSED
        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/{cycleId}/snapshots")
    public ResponseEntity<CycleHistoryDetailDTO> getCycleSnapshots(
            @PathVariable UUID cycleId,
            @RequestAttribute("userId") UUID userId) {
        
        CycleHistoryDetailDTO detailDTO = historyService.getCycleSnapshots(cycleId, userId);
        return ResponseEntity.ok(detailDTO);
    }
}