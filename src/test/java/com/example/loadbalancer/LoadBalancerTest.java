package com.example.loadbalancer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

// Add these static imports!
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class LoadBalancerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testLoadDistribution() throws Exception {
        Map<String, Integer> serverHits = new HashMap<>();

        for (int i = 0; i < 100; i++) {
            MvcResult result = mockMvc.perform(get("/"))  // Now this will work!
                    .andExpect(status().isOk())
                    .andReturn();

            String response = result.getResponse().getContentAsString();
            // Parse response and count hits per server
        }

        // Assert distribution matches expected pattern
    }
}