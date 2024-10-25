package it.eng.dome.billing.scheduler.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
public class BillingSchedulerControllerTest {

    @Autowired
    private MockMvc mockMvc;


    @Value("${application.name}")
    private String appName;

    @Value("${build.version}")
    private String buildVersion;

    @Test
    public void shouldReturnExpectedMessage() throws Exception {

        mockMvc.perform(get("/scheduler/info").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(buildVersion))
            .andExpect(jsonPath("$.name").value(appName));
    }
}
