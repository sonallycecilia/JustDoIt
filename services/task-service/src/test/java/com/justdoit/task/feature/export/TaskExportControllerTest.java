package com.justdoit.task.feature.export;

import com.justdoit.common.security.JwtValidator;
import com.justdoit.task.shared.ExportFormat;
import com.justdoit.task.shared.TaskExportResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AC2 (escolha de formato) na borda HTTP: o mesmo endpoint devolve CSV ou JSON
 * conforme ?format=, sempre como anexo com nome carimbado pela data.
 */
@WebMvcTest(TaskExportController.class)
class TaskExportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TaskExportService taskExportService;
    @MockitoBean private JwtValidator jwtValidator;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private TaskExportResponse exportVazio() {
        return new TaskExportResponse(LocalDateTime.of(2026, 7, 27, 12, 0), USER_ID, 0, List.of());
    }

    @Test
    @DisplayName("format=json devolve o envelope JSON como anexo .json")
    void export_json() throws Exception {
        when(taskExportService.export(USER_ID)).thenReturn(exportVazio());
        when(taskExportService.fileName(eq(ExportFormat.JSON), any())).thenReturn("export_tarefas_2026-07-27.json");

        mockMvc.perform(get("/me/export").param("format", "json").with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export_tarefas_2026-07-27.json\""))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.taskCount").value(0))
                .andExpect(jsonPath("$.tasks").isArray());

        verify(taskExportService, never()).toCsv(any());
    }

    @Test
    @DisplayName("format=csv devolve text/csv como anexo .csv")
    void export_csv() throws Exception {
        when(taskExportService.export(USER_ID)).thenReturn(exportVazio());
        when(taskExportService.toCsv(any())).thenReturn("id,title\r\n");
        when(taskExportService.fileName(eq(ExportFormat.CSV), any())).thenReturn("export_tarefas_2026-07-27.csv");

        mockMvc.perform(get("/me/export").param("format", "csv").with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export_tarefas_2026-07-27.csv\""))
                .andExpect(content().string("id,title\r\n"));
    }

    @Test
    @DisplayName("sem format o padrão é JSON")
    void export_semFormato_ehJson() throws Exception {
        when(taskExportService.export(USER_ID)).thenReturn(exportVazio());
        when(taskExportService.fileName(eq(ExportFormat.JSON), any())).thenReturn("export_tarefas_2026-07-27.json");

        mockMvc.perform(get("/me/export").with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @DisplayName("formato desconhecido é 400 e nem chega a consultar o banco")
    void export_formatoInvalido_ehBadRequest() throws Exception {
        mockMvc.perform(get("/me/export").param("format", "xlsx").with(authenticatedUser(USER_ID)))
                .andExpect(status().isBadRequest());

        verify(taskExportService, never()).export(any());
    }

    @Test
    @DisplayName("exporta o dono do token, ignorando qualquer userId enviado pelo cliente")
    void export_usaSempreOUsuarioDoToken() throws Exception {
        UUID outro = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(taskExportService.export(USER_ID)).thenReturn(exportVazio());
        when(taskExportService.fileName(any(), any())).thenReturn("export_tarefas_2026-07-27.json");

        mockMvc.perform(get("/me/export").param("userId", outro.toString()).with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk());

        verify(taskExportService).export(USER_ID);
        verify(taskExportService, never()).export(outro);
    }

    @Test
    @DisplayName("nome do arquivo é gerado com a data de hoje")
    void export_nomeUsaDataDeHoje() throws Exception {
        when(taskExportService.export(USER_ID)).thenReturn(exportVazio());
        when(taskExportService.fileName(any(), any())).thenReturn("export_tarefas.json");

        mockMvc.perform(get("/me/export").with(authenticatedUser(USER_ID))).andExpect(status().isOk());

        verify(taskExportService).fileName(ExportFormat.JSON, LocalDate.now());
    }
}
