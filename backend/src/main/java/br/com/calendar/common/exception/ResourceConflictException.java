package br.com.calendar.common.exception;



public class ResourceConflictException extends IllegalStateException {
    
    public ResourceConflictException(String message) {
        super(message);
    }
}