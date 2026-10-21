package com.scmarket.api;

public final class ApiResponseException extends ApiException {
    public ApiResponseException(String message) {
        super(message, 200, false);
    }

    public ApiResponseException(String message, Throwable cause) {
        super(message, 200, false, cause);
    }
}
