package com.group1.career.service.impl;

import com.group1.career.common.ErrorCode;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.CareerNode;
import com.group1.career.model.entity.CareerPath;
import com.group1.career.model.entity.UserCareerProgress;
import com.group1.career.repository.CareerNodeRepository;
import com.group1.career.repository.CareerPathRepository;
import com.group1.career.repository.UserCareerProgressRepository;
import com.group1.career.service.CareerService;
import com.group1.career.service.CheckInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerServiceImpl implements CareerService {

    private final CareerPathRepository pathRepository;
    private final CareerNodeRepository nodeRepository;
    private final UserCareerProgressRepository progressRepository;
    private final CheckInService checkInService;

    @Override
    public List<CareerPath> getAllPaths() {
        return pathRepository.findAll();
    }

    @Override
    public CareerPath getPathById(Integer pathId) {
        return pathRepository.findById(pathId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR));
    }

    @Override
    public List<CareerNode> getPathNodes(Integer pathId) {
        return nodeRepository.findByPathIdOrderByLevelAsc(pathId);
    }

    @Override
    public List<UserCareerProgress> getUserProgress(Long authenticatedUserId) {
        return progressRepository.findByUserId(authenticatedUserId);
    }

    @Override
    @Transactional
    public void unlockNode(Long authenticatedUserId, Long nodeId) {
        UserCareerProgress progress = progressRepository.findByUserIdAndNodeId(authenticatedUserId, nodeId)
                .orElse(UserCareerProgress.builder()
                        .userId(authenticatedUserId)
                        .nodeId(nodeId)
                        .status("UNLOCKED")
                        .build());

        progress.setStatus("UNLOCKED");

        progressRepository.save(progress);
    }

    @Override
    @Transactional
    public void completeNode(Long authenticatedUserId, Long nodeId) {
        UserCareerProgress progress = progressRepository.findByUserIdAndNodeId(authenticatedUserId, nodeId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR));

        progress.setStatus("COMPLETED");
        progressRepository.save(progress);

        // Daily check-in. Best-effort.
        try {
            checkInService.recordAction(authenticatedUserId, "SKILL_NODE");
        } catch (Exception e) {
            log.warn("[career] check-in record failed for user {}: {}", authenticatedUserId, e.toString());
        }
    }

    @Override
    @Transactional
    public void initializeDefaultPaths() {
        CareerPath javaPath = pathRepository.findByCode("java-backend")
                .orElse(CareerPath.builder().code("java-backend").build());
        javaPath.setName("Java 后端工程师");
        javaPath.setDescription("成为优秀的 Java 后端开发者，掌握 Spring Boot、微服务、数据库等核心技能。");
        javaPath = pathRepository.save(javaPath);

        if (nodeRepository.findByPathIdOrderByLevelAsc(javaPath.getPathId()).isEmpty()) {
            CareerNode node1 = nodeRepository.save(CareerNode.builder()
                    .pathId(javaPath.getPathId()).name("Java 基础").level(1).parentId(0L).build());
            CareerNode node2 = nodeRepository.save(CareerNode.builder()
                    .pathId(javaPath.getPathId()).name("Spring Boot 入门").level(2).parentId(node1.getNodeId()).build());
            nodeRepository.save(CareerNode.builder()
                    .pathId(javaPath.getPathId()).name("数据库设计").level(2).parentId(node1.getNodeId()).build());
            nodeRepository.save(CareerNode.builder()
                    .pathId(javaPath.getPathId()).name("Spring Cloud 微服务").level(3).parentId(node2.getNodeId()).build());
        }

        CareerPath frontendPath = pathRepository.findByCode("frontend-engineer")
                .orElse(CareerPath.builder().code("frontend-engineer").build());
        frontendPath.setName("前端工程师");
        frontendPath.setDescription("成为现代前端开发者，掌握 Vue、React、TypeScript 等主流技术栈。");
        frontendPath = pathRepository.save(frontendPath);

        if (nodeRepository.findByPathIdOrderByLevelAsc(frontendPath.getPathId()).isEmpty()) {
            CareerNode fe1 = nodeRepository.save(CareerNode.builder()
                    .pathId(frontendPath.getPathId()).name("HTML/CSS 基础").level(1).parentId(0L).build());
            CareerNode fe2 = nodeRepository.save(CareerNode.builder()
                    .pathId(frontendPath.getPathId()).name("JavaScript 核心").level(2).parentId(fe1.getNodeId()).build());
            nodeRepository.save(CareerNode.builder()
                    .pathId(frontendPath.getPathId()).name("Vue.js 框架").level(3).parentId(fe2.getNodeId()).build());
        }

        log.info("Initialized/Updated career paths: Java 后端工程师, 前端工程师");
    }
}
