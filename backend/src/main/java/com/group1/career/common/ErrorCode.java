package com.group1.career.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(200, "Success"),
    SYSTEM_ERROR(500, "System Error"),
    PARAM_ERROR(400, "Parameter Error"),
    UNAUTHORIZED_ERROR(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    USER_NOT_FOUND(4001, "User Not Found"),
    USER_ALREADY_EXISTS(4002, "User Already Exists"),
    RESUME_NOT_FOUND(4003, "Resume Not Found"),
    ACCOUNT_DELETED(410, "Account has been deleted"),
    ACCOUNT_BANNED(403, "Account has been banned");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
