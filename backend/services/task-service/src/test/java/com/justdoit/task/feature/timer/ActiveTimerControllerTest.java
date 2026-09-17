package com.justdoit.task.feature.timer;

import com.justdoit.common.security.JwtValidator;
import com.justdoit.task.shared.ActiveTimerResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActiveTimerController.class)
class ActiveTimerControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TaskTimerService timerService;
    @MockitoBean private JwtValidator jwtValidator;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ACTIVE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void getActive_returnsOk() throws Exception {
        when(timerService.getActive(USER_ID))
                .thenReturn(new ActiveTimerResponse(ACTIVE_ID, TASK_ID, LocalDateTime.now()));

        mockMvc.perform(get("/timers/active").with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(TASK_ID.toString()));
    }

    @Test
    void getActive_whenSemCronometroAtivo_returns404() throws Exception {
        when(timerService.getActive(USER_ID)).thenThrow(new IllegalArgumentException("no active timer"));

        mockMvc.perform(get("/timers/active").with(authenticatedUser(USER_ID)))
                .andExpect(status().isNotFound());
    }
}
