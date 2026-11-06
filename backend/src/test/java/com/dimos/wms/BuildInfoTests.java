package com.dimos.wms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The build version shown on the login page and in the footer comes from /actuator/info (open without login). */
@SpringBootTest
@AutoConfigureMockMvc
class BuildInfoTests {

    @Autowired
    MockMvc mvc;

    @Test
    void infoShowsVersionBuildTimeAndCommitWithoutLogin() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.version").value("0.1.0"))
                .andExpect(jsonPath("$.build.time").isNotEmpty())
                .andExpect(jsonPath("$.git.commit.id").isNotEmpty())
                .andExpect(jsonPath("$.git.branch").isNotEmpty());
    }

    @Test
    void infoRevealsNothingAboutTheMachineOrPeople() throws Exception {
        String body = mvc.perform(get("/actuator/info")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"env\"", "\"os\"", "\"java\"", "\"process\"", "@", "user.email", "message");
    }
}
