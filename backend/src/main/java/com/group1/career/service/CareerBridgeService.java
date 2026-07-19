package com.group1.career.service;

import com.group1.career.model.dto.CareerBridgeDtos.ConsumeLinkCodeResponse;
import com.group1.career.model.dto.CareerBridgeDtos.InterventionRequest;
import com.group1.career.model.dto.CareerBridgeDtos.InterventionResponse;
import com.group1.career.model.dto.CareerBridgeDtos.LinkCodeResponse;
import com.group1.career.model.dto.CareerBridgeDtos.StudentPassportResponse;

public interface CareerBridgeService {

    LinkCodeResponse issueLinkCode(Long userId);

    ConsumeLinkCodeResponse consumeLinkCode(String code);

    StudentPassportResponse getStudentPassport(Long studentId);

    InterventionResponse createIntervention(Long studentId, InterventionRequest request);
}
