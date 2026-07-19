package com.group1.career.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.model.dto.UserProfileSnapshot;
import com.group1.career.model.entity.User;
import com.group1.career.repository.ResumeRepository;
import com.group1.career.repository.UserFactRepository;
import com.group1.career.repository.UserRepository;
import com.group1.career.service.impl.UserProfileSnapshotServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileSnapshotServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserFactRepository userFactRepository;

    @Mock
    private ResumeRepository resumeRepository;

    private ObjectMapper objectMapper;
    private UserProfileSnapshotServiceImpl snapshotService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        snapshotService = new UserProfileSnapshotServiceImpl(
                userRepository, userFactRepository, resumeRepository, objectMapper);
    }

    @Test
    @DisplayName("mergeOnboarding patches education fields without erasing degree")
    void mergeOnboarding_PreservesUneditedEducationFields() throws Exception {
        Long userId = 9L;
        UserProfileSnapshot existing = UserProfileSnapshot.builder()
                .onboarding(UserProfileSnapshot.OnboardingBlock.builder()
                        .timeline("within_3_months")
                        .weeklyAvailability("5_10h")
                        .education(UserProfileSnapshot.EducationBlock.builder()
                                .school("旧学校")
                                .major("软件工程")
                                .degree("本科")
                                .graduationYear("2027")
                                .build())
                        .build())
                .build();
        User user = User.builder()
                .userId(userId)
                .profileSnapshot(objectMapper.writeValueAsString(existing))
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(resumeRepository.findByUserId(userId)).thenReturn(List.of());

        snapshotService.mergeOnboarding(userId, UserProfileSnapshot.OnboardingBlock.builder()
                .education(UserProfileSnapshot.EducationBlock.builder()
                        .school("新学校")
                        .graduationYear("")
                        .build())
                .build());

        UserProfileSnapshot saved = objectMapper.readValue(
                user.getProfileSnapshot(), UserProfileSnapshot.class);
        UserProfileSnapshot.EducationBlock education = saved.getOnboarding().getEducation();
        assertEquals("新学校", education.getSchool());
        assertEquals("软件工程", education.getMajor());
        assertEquals("本科", education.getDegree());
        assertEquals("", education.getGraduationYear());
    }
}
