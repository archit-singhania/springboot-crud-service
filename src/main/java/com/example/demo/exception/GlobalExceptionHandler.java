package com.example.demo.exception;

import com.example.demo.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==========================
    // NoHandlerFoundException - 404 for undefined endpoints
    // ==========================
    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNoHandlerFound(HttpServletRequest request, NoHandlerFoundException ex) {
        String acceptHeader = request.getHeader("Accept");

        // Browser request - return HTML view
        if (acceptHeader != null && acceptHeader.contains("text/html")) {
            ModelAndView mav = new ModelAndView("custom-error");
            mav.addObject("status", 404);
            mav.addObject("message", "Oops! The endpoint '" + ex.getRequestURL() + "' does not exist.");
            return mav;
        }

        // API request (Postman) - return JSON
        ApiResponse<String> apiResponse = new ApiResponse<>(
                "failure",
                "Endpoint not found: " + ex.getRequestURL(),
                null,
                HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiResponse);
    }

    // ==========================
    // NotFoundException for API
    // ==========================
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNotFound(NotFoundException e) {
        ApiResponse<String> apiResponse = new ApiResponse<>(
                "failure",
                e.getMessage(),
                null,
                HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiResponse);
    }

    // ==========================
    // IllegalArgumentException
    // ==========================
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<String>> handleIllegalArgument(IllegalArgumentException e) {
        ApiResponse<String> apiResponse = new ApiResponse<>(
                "failure",
                e.getMessage(),
                null,
                HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    // ==========================
    // Validation errors for @Valid request bodies
    // ==========================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error instanceof FieldError
                    ? ((FieldError) error).getField()
                    : "unknown";
            errors.put(fieldName, error.getDefaultMessage());
        });

        ApiResponse<Map<String, String>> apiResponse = new ApiResponse<>(
                "failure",
                "Validation failed",
                errors,
                HttpStatus.BAD_REQUEST.value()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    // ==========================
    // Constraint violations (e.g., path/query params)
    // ==========================
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintValidation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getConstraintViolations().forEach(v -> {
            String field = v.getPropertyPath().toString();
            if (field.contains(".")) {
                field = field.substring(field.lastIndexOf('.') + 1);
            }
            errors.put(field, v.getMessage());
        });

        ApiResponse<Map<String, String>> apiResponse = new ApiResponse<>(
                "failure",
                "Constraint validation failed",
                errors,
                HttpStatus.BAD_REQUEST.value()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    // ==========================
    // Generic exceptions
    // ==========================
    @ExceptionHandler(Exception.class)
    public Object handleGenericException(HttpServletRequest request, Exception e) {
        String acceptHeader = request.getHeader("Accept");

        // Browser request - return HTML view
        if (acceptHeader != null && acceptHeader.contains("text/html")) {
            ModelAndView mav = new ModelAndView("custom-error");
            mav.addObject("status", 500);
            mav.addObject("message", "Something went wrong on the server.");
            return mav;
        }

        // API request (Postman) - return JSON
        ApiResponse<String> apiResponse = new ApiResponse<>(
                "failure",
                "Something went wrong: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiResponse);
    }
}