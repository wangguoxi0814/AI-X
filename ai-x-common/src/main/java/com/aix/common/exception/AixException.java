package com.aix.common.exception;

import com.aix.common.model.ApiCode;

public class AixException extends RuntimeException {

    private final ApiCode code;

    public AixException(ApiCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiCode getCode() {
        return code;
    }
}
