package com.group1.career.service;

import com.group1.career.model.entity.CareerNode;
import com.group1.career.model.entity.CareerPath;
import com.group1.career.model.entity.UserCareerProgress;

import java.util.List;

public interface CareerService {
    /**
     * Get all career paths
     */
    List<CareerPath> getAllPaths();

    /**
     * Get career path by ID
     */
    CareerPath getPathById(Integer pathId);

    /**
     * Get all nodes for a specific path
     */
    List<CareerNode> getPathNodes(Integer pathId);

    /**
     * Get career progress for a trusted server-side user id. HTTP callers
     * must derive this id from the authenticated request, never request data.
     */
    List<UserCareerProgress> getUserProgress(Long authenticatedUserId);

    /**
     * Unlock a node for a trusted server-side user id.
     */
    void unlockNode(Long authenticatedUserId, Long nodeId);

    /**
     * Complete a node for a trusted server-side user id.
     */
    void completeNode(Long authenticatedUserId, Long nodeId);

    /**
     * Initialize default career paths (for testing)
     */
    void initializeDefaultPaths();
}
