package com.justdoit.task.feature.moduleconfig;
import com.justdoit.task.feature.task.Task;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_module_config")
public class TaskModuleConfig {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", unique = true, nullable = false)
    private Task task;

    @Builder.Default
    @Column(name = "focus_enabled")
    private Boolean focusEnabled = false;

    @Builder.Default
    @Column(name = "cycle_enabled")
    private Boolean cycleEnabled = false;

    @Builder.Default
    @Column(name = "priority_enabled")
    private Boolean priorityEnabled = false;

    @Builder.Default
    @Column(name = "timer_enabled")
    private Boolean timerEnabled = false;

    @Builder.Default
    @Column(name = "notes_enabled")
    private Boolean notesEnabled = false;
}
