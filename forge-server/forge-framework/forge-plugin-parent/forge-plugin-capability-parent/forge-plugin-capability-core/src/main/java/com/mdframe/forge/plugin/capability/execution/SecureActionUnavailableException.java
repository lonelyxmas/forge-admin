package com.mdframe.forge.plugin.capability.execution;

import lombok.Getter;

@Getter
public class SecureActionUnavailableException extends RuntimeException {

    private final String errorCode;

    public SecureActionUnavailableException(String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
    }
}
