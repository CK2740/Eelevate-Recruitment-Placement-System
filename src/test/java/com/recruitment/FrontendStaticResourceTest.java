package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class FrontendStaticResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void productionReactEntryAndAssetsAreServedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString();
        Matcher script = Pattern.compile("src=\"\\./(assets/[^\"]+\\.js)\"").matcher(html);
        Matcher stylesheet = Pattern.compile("href=\"\\./(assets/[^\"]+\\.css)\"").matcher(html);
        assertTrue(script.find(), "React entry bundle must be referenced from the static index");
        assertTrue(stylesheet.find(), "React stylesheet must be referenced from the static index");
        mockMvc.perform(get("/" + script.group(1)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/" + stylesheet.group(1)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }
}
