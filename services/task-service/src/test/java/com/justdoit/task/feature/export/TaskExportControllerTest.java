package com.justdoit.task.feature.export;

import com.justdoit.common.security.JwtValidator;
import com.justdoit.task.config.WebSecurityConfig;
import com.justdoit.task.shared.ExportFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskExportController.class)
@Import(WebSecurityConfig.class)
class TaskExportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExportJobService exportJobService;
    @MockitoBean private JwtValidator jwtValidator;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Test
    @DisplayName("POST aceita imediatamente com 202, jobId e Location")
    void requestExport_returns202() throws Exception {
        when(exportJobService.request(USER_ID, ExportFormat.CSV)).thenReturn(
                new ExportJobAcceptedResponse(JOB_ID, ExportJobStatus.PENDING,
                        "/me/exports/" + JOB_ID, LocalDateTime.now()));

        mockMvc.perform(post("/me/exports").param("format", "csv")
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/me/exports/" + JOB_ID))
                .andExpect(jsonPath("$.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET legado também aceita como job e não gera arquivo na request")
    void legacyGet_returns202() throws Exception {
        when(exportJobService.request(USER_ID, ExportFormat.JSON)).thenReturn(
                new ExportJobAcceptedResponse(JOB_ID, ExportJobStatus.PENDING,
                        "/me/exports/" + JOB_ID, LocalDateTime.now()));

        mockMvc.perform(get("/me/export").with(authenticatedUser(USER_ID)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("formato desconhecido é 400 sem criar job")
    void invalidFormat_returns400() throws Exception {
        mockMvc.perform(post("/me/exports").param("format", "xlsx")
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isBadRequest());

        verify(exportJobService, never()).request(any(), any());
    }

    @Test
    @DisplayName("status usa sempre o usuário autenticado")
    void status_isOwnedByPrincipal() throws Exception {
        when(exportJobService.status(JOB_ID, USER_ID)).thenReturn(new ExportJobResponse(
                JOB_ID, ExportFormat.JSON, ExportJobStatus.RUNNING,
                LocalDateTime.now(), LocalDateTime.now(), null, null,
                null, null, null, null, null));

        mockMvc.perform(get("/me/exports/{id}", JOB_ID).with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        verify(exportJobService).status(JOB_ID, USER_ID);
    }

    @Test
    @DisplayName("download assinado é público e preserva Content-Disposition")
    void signedDownload_returnsFile() throws Exception {
        when(exportJobService.download(JOB_ID, 123L, "signed")).thenReturn(
                new ExportDownload(new ByteArrayResource("id,title\r\n".getBytes()),
                        "export_tarefas.csv", "text/csv;charset=UTF-8"));

        mockMvc.perform(get("/me/exports/{id}/download", JOB_ID)
                        .param("expires", "123").param("token", "signed"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''export_tarefas.csv")))
                .andExpect(content().string("id,title\r\n"));
    }
}
