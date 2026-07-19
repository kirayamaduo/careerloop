package com.group1.career.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.interceptor.AuthInterceptor;
import com.group1.career.model.dto.UserProfileSnapshot;
import com.group1.career.model.entity.User;
import com.group1.career.service.AgentProfileService;
import com.group1.career.service.CareerPlanService;
import com.group1.career.service.UserProfileSnapshotService;
import com.group1.career.service.UserProfileTagService;
import com.group1.career.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// register/login have been moved to AuthController (/auth/*), tested in AuthControllerTest.
@WebMvcTest(UserController.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    /**
     * Required so the controller can inject this dep when the
     * UserController test slice constructs the bean. The snapshot endpoints
     * aren't exercised in this file, so a default mock (returns null) is fine.
     */
    @MockitoBean
    private UserProfileSnapshotService snapshotService;

    @MockitoBean
    private CareerPlanService careerPlanService;

    @MockitoBean
    private AgentProfileService agentProfileService;

    @MockitoBean
    private UserProfileTagService userProfileTagService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    public void bypassAuth() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("GET /users/{id} - Returns user info")
    public void testGetUser_Success() throws Exception {
        Long userId = 1L;
        User mockUser = new User();
        mockUser.setUserId(userId);
        mockUser.setNickname("ExistingUser");

        when(userService.getUserById(userId)).thenReturn(mockUser);
        // Controller hydrates the avatar presigned URL just before returning;
        // the no-op identity stub keeps the test focused on the JSON shape
        // without pulling in the OSS SDK.
        when(userService.hydrateUrl(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(get("/users/{id}", userId).requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.nickname").value("ExistingUser"));
    }

    @Test
    @DisplayName("PUT /users/{id} - explicitly clears nullable graduation year")
    public void testUpdateUser_ClearsGraduationYear() throws Exception {
        Long userId = 7L;
        User updated = new User();
        updated.setUserId(userId);
        updated.setNickname("Student");
        updated.setGraduationYear(null);

        when(userService.updateUser(
                eq(userId), eq("Student"), eq(null), eq(""), eq(""),
                eq(null), eq(true))).thenReturn(updated);
        when(userService.hydrateUrl(updated)).thenReturn(updated);

        mockMvc.perform(put("/users/{id}", userId)
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "Student",
                                  "school": "",
                                  "major": "",
                                  "clearGraduationYear": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.graduationYear").doesNotExist());

        verify(userService).updateUser(
                userId, "Student", null, "", "", null, true);
        verify(snapshotService).mergeOnboarding(eq(userId), argThat(block ->
                block.getEducation() != null
                        && "".equals(block.getEducation().getSchool())
                        && "".equals(block.getEducation().getMajor())
                        && "".equals(block.getEducation().getGraduationYear())
                        && block.getEducation().getDegree() == null));
        verify(userProfileTagService).refreshFromSignals(userId);
    }

    @Test
    @DisplayName("PUT /users/me/profile-snapshot/onboarding - persists onboarding block and target role preference")
    public void testUpdateOnboarding_PersistsBlocks() throws Exception {
        Long userId = 7L;
        UserProfileSnapshot snapshot = UserProfileSnapshot.builder()
                .onboarding(UserProfileSnapshot.OnboardingBlock.builder()
                        .identityType("internship_seeker")
                        .hasResume("no")
                        .onboardingCompletedAt("2026-05-14T10:00:00.000Z")
                        .build())
                .preferences(UserProfileSnapshot.PreferencesBlock.builder()
                        .targetRole("前端开发工程师")
                        .build())
                .build();
        when(snapshotService.read(userId)).thenReturn(snapshot);

        mockMvc.perform(put("/users/me/profile-snapshot/onboarding")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identityType", "internship_seeker",
                                "hasResume", "no",
                                "onboardingCompletedAt", "2026-05-14T10:00:00.000Z",
                                "targetRole", "前端开发工程师"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarding.identityType").value("internship_seeker"))
                .andExpect(jsonPath("$.data.onboarding.hasResume").value("no"))
                .andExpect(jsonPath("$.data.onboarding.onboardingCompletedAt").value("2026-05-14T10:00:00.000Z"))
                .andExpect(jsonPath("$.data.preferences.targetRole").value("前端开发工程师"));

        verify(snapshotService).mergeOnboarding(eq(userId), argThat(block ->
                "internship_seeker".equals(block.getIdentityType())
                        && "no".equals(block.getHasResume())
                        && "2026-05-14T10:00:00.000Z".equals(block.getOnboardingCompletedAt())));
        verify(snapshotService).mergePreferences(eq(userId), argThat(block ->
                "前端开发工程师".equals(block.getTargetRole())));
        verify(careerPlanService).regenerateWithRoleAsync(userId, "前端开发工程师");
        verify(agentProfileService).refresh(userId);
    }

    @Test
    @DisplayName("PUT /users/me/profile-snapshot/onboarding - explicit blank education clears relational fields consistently")
    public void testUpdateOnboarding_ClearsEducationFieldsConsistently() throws Exception {
        Long userId = 7L;
        when(snapshotService.read(userId)).thenReturn(UserProfileSnapshot.builder().build());

        mockMvc.perform(put("/users/me/profile-snapshot/onboarding")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "education": {
                                    "school": "  ",
                                    "major": "",
                                    "degree": "本科",
                                    "graduationYear": " "
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        verify(snapshotService).mergeOnboarding(eq(userId), argThat(block ->
                block.getEducation() != null
                        && "".equals(block.getEducation().getSchool())
                        && "".equals(block.getEducation().getMajor())
                        && "本科".equals(block.getEducation().getDegree())
                        && "".equals(block.getEducation().getGraduationYear())));
        verify(userService).updateUser(userId, null, null, "", "", null, true);
    }
}
