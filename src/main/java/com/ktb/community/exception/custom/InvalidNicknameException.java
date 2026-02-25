package com.ktb.community.exception.custom;

public class InvalidNicknameException extends RuntimeException {
    public InvalidNicknameException(String message) {
        super(message);
    }
}
