package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.EvidenceRfpApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EvidenceRfpApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.task.scheduling.enabled=false")
@Import(ApiExceptionHandlerWebMvcTest.FailingEndpoint.class)
class ApiExceptionHandlerWebMvcTest {
    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void returnsProblemDetailForUnknownApiPath() throws Exception {
        mvc.perform(get("/api/v1/unknown-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("API_RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/unknown-resource"));
    }

    @Test
    void returnsProblemDetailAndPreservesAllowHeaderForWrongMethod() throws Exception {
        mvc.perform(post("/api/v1/capabilities"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void returnsProblemDetailForUnacceptableResponseType() throws Exception {
        mvc.perform(get("/api/v1/capabilities").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    void catchAllDoesNotExposeUnexpectedExceptionDetails() throws Exception {
        mvc.perform(get("/api/v1/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(
                        "Die Anfrage konnte wegen eines internen Fehlers nicht verarbeitet werden."))
                .andExpect(content().string(not(containsString("sensitive-provider-secret"))));
    }

    @RestController
    static class FailingEndpoint {
        @GetMapping("/api/v1/test/unexpected")
        String fail() {
            throw new RuntimeException("sensitive-provider-secret");
        }
    }
}
