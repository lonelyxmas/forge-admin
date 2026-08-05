package com.mdframe.forge.plugin.capability.opengateway.exception;

import lombok.Getter;

/**
 * 开放网关业务异常：携带对外契约错误码与 HTTP 状态码。
 */
@Getter
public class OpenGatewayException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public OpenGatewayException(String errorCode, int httpStatus, String message) {
        this(errorCode, httpStatus, message, null);
    }

    public OpenGatewayException(String errorCode, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
