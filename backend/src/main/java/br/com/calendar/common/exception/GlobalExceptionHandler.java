package br.com.calendar.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<@NonNull Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                           HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid fields");
        problem.setProperty("fields", fields);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    @ExceptionHandler(InvalidTaskTimeRangeException.class)
    ProblemDetail handleInvalidTaskTimeRangeException(InvalidTaskTimeRangeException e, HttpServletRequest request) {
        logger.warn("Task with invalid time range: {}", request.getRequestURI(), e);
https://github.com/Tecnologia-da-Informacao-BR/Calendar/pull/123/conflict?name=backend%252Fsrc%252Fmain%252Fjava%252Fbr%252Fcom%252Fcalendar%252Fcommon%252Fexception%252FGlobalExceptionHandler.java&ancestor_oid=aac349e9614c45847d95264f200ee818a3ce4163&base_oid=0a273a78f75839b72b2cd9a43cc83948d3fffb46&head_oid=191c3a6642a6b5f9b9df68331255f7db1e9f4fee
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFoundException(UserNotFoundException e, HttpServletRequest request) {
        logger.warn("User not found: {}", request.getRequestURI(), e);

        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    ProblemDetail handleCategoryNotFoundException(CategoryNotFoundException e, HttpServletRequest request) {
        logger.warn("Category not found: {}", request.getRequestURI(), e);

        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NotAuthenticatedException.class)
    ProblemDetail handleNotAuthenticatedException(NotAuthenticatedException e, HttpServletRequest request) {
        logger.warn("Not authenticated: {}", request.getRequestURI(), e);

        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
}
