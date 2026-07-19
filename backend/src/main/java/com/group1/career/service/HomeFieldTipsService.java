package com.group1.career.service;

import com.group1.career.model.dto.HomeConsultationFeedDto;
import com.group1.career.model.entity.HomeArticle;
import com.group1.career.model.entity.HomeConsultation;

import java.util.List;

/**
 * Builds the home-page "From the Field" consultation cards.
 *
 * Public and personalised entry points are intentionally separate so an
 * anonymous controller path can never accidentally pass a caller-supplied
 * user id into profile, memory, interview, or AI services.
 */
public interface HomeFieldTipsService {

    List<HomeConsultationFeedDto> buildPublicConsultationFeed(
            long seed,
            int limit,
            List<HomeConsultation> consultationPool,
            List<HomeArticle> articlePool
    );

    List<HomeConsultationFeedDto> buildPersonalizedConsultationFeed(
            long authenticatedUserId,
            long seed,
            int limit,
            List<HomeConsultation> consultationPool,
            List<HomeArticle> articlePool
    );
}
