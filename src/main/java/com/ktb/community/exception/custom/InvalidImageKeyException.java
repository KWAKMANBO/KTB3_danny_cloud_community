package com.ktb.community.exception.custom;

public class InvalidImageKeyException extends RuntimeException {
    public InvalidImageKeyException(String message) {
        super(message);
    }
}