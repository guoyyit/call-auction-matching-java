package com.github.guoyyit.callauctionmatchingjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "call-auction.log-file=target/test-data/http-api.log",
        "call-auction.reset-on-startup=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureMockMvc
public class HttpApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void fullWorkflowThroughHttp() throws Exception {
        mockMvc.perform(post("/api/auction/start")
                        .contentType("application/json")
                        .content("{\"kind\":\"OPEN\",\"referencePrice\":\"10.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("OPEN"));

        mockMvc.perform(post("/api/auction/orders")
                        .contentType("application/json")
                        .content("{\"clientOrderId\":\"h1\",\"symbol\":\"SEC\",\"side\":\"BUY\","
                                + "\"limitPrice\":\"10.05\",\"market\":false,\"quantity\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/auction/orders")
                        .contentType("application/json")
                        .content("{\"clientOrderId\":\"h2\",\"symbol\":\"SEC\",\"side\":\"SELL\","
                                + "\"limitPrice\":\"10.00\",\"market\":false,\"quantity\":60}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auction/orders/cancel")
                        .contentType("application/json")
                        .content("{\"clientOrderId\":\"h2\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auction/orders/h1/replace")
                        .contentType("application/json")
                        .content("{\"clientOrderId\":\"h3\",\"limitPrice\":\"10.05\","
                                + "\"market\":false,\"quantity\":80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientOrderId").value("h3"));

        mockMvc.perform(get("/api/auction/imbalance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").exists());

        mockMvc.perform(post("/api/auction/orders")
                        .contentType("application/json")
                        .content("{\"clientOrderId\":\"h4\",\"symbol\":\"SEC\",\"side\":\"SELL\","
                                + "\"limitPrice\":\"10.00\",\"market\":false,\"quantity\":80}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auction/finish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedQuantity").value(80));

        mockMvc.perform(get("/api/auction/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchedQuantity").value(80));
    }

    @Test
    public void rejectsCommandsOutsideCollectingPhase() throws Exception {
        mockMvc.perform(post("/api/auction/finish"))
                .andExpect(status().isConflict());
    }

    @Test
    public void rejectsInvalidOrderPayload() throws Exception {
        mockMvc.perform(post("/api/auction/start")
                        .contentType("application/json")
                        .content("{\"kind\":\"OPEN\",\"referencePrice\":\"10.00\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auction/orders")
                        .contentType("application/json")
                        .content("{\"clientOrderId\":\"x\",\"side\":\"BUY\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }
}
