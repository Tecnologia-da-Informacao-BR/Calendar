package br.com.calendar.common.exception;

public class InvalidTaskTimeRangeException extends RuntimeException {
    public InvalidTaskTimeRangeException(String message) {
        super(message);
    }
}
