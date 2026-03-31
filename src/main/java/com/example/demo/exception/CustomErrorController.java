package com.example.demo.exception;

import com.example.demo.dto.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class CustomErrorController implements ErrorController {

    private static final String ERROR_PATH = "/error";

    @RequestMapping(value = ERROR_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView handleErrorHtml(HttpServletRequest request) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int errorStatus = statusObj != null ? Integer.parseInt(statusObj.toString()) : 500;

        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String message;

        if (errorStatus == 404) {
            message = "Oops! The endpoint '" + (path != null ? path : "unknown") + "' does not exist.";
        } else if (errorStatus == 500) {
            message = "Something went wrong on the server.";
        } else {
            message = "An error occurred.";
        }

        ModelAndView mav = new ModelAndView();
        mav.setViewName("custom-error");
        mav.addObject("status", errorStatus);
        mav.addObject("message", message);
        return mav;
    }

    @RequestMapping(value = ERROR_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<String>> handleErrorJson(HttpServletRequest request) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int errorStatus = statusObj != null ? Integer.parseInt(statusObj.toString()) : 500;

        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String message;

        if (errorStatus == 404) {
            message = "Endpoint not found: " + (path != null ? path : "unknown");
        } else {
            message = "Internal server error";
        }

        ApiResponse<String> response = new ApiResponse<>(
                "failure",
                message,
                null,
                errorStatus
        );

        return ResponseEntity.status(errorStatus).body(response);
    }
}