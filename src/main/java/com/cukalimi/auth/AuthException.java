package com.cukalimi.auth;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static AuthException unauthorized() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
    }

    public static AuthException forbidden() {
        return new AuthException(HttpStatus.FORBIDDEN, "권한이 없습니다");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
