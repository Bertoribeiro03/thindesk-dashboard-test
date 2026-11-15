package com.pfc.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.*;

@ControllerAdvice
public class ErrorHandlers {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandlers.class);
    @ExceptionHandler(ApiError.class) public Object api(ApiError exception, HttpServletRequest request) { return response(exception, request); }
    @ExceptionHandler(DataAccessException.class) public Object database(DataAccessException exception, HttpServletRequest request) {
        log.error("Falha de acesso ao banco de dados.", exception);
        return response(new ApiError(503, "DATABASE_UNAVAILABLE", "Banco de dados indisponível."), request);
    }
    @ExceptionHandler(NoResourceFoundException.class) public Object notFound(Exception exception, HttpServletRequest request) {
        return response(new ApiError(404, "NOT_FOUND", "Recurso não encontrado."), request);
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class) public Object method(Exception exception, HttpServletRequest request) {
        return response(new ApiError(405, "HTTP_ERROR", "Método HTTP não permitido."), request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) public Object validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var details = exception.getBindingResult().getFieldErrors().stream().map(error -> Map.of("param", error.getField(), "msg", Objects.requireNonNullElse(error.getDefaultMessage(), "Valor inválido."))).toList();
        return response(new ApiError(400, "VALIDATION_ERROR", "Parâmetros inválidos.", details), request);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class}) public Object malformed(Exception exception, HttpServletRequest request) {
        return response(ApiError.validation("request", "Verifique o formato dos parâmetros e do corpo da requisição."), request);
    }
    @ExceptionHandler(Exception.class) public Object unexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof org.springframework.web.ErrorResponse error) {
            return response(new ApiError(error.getStatusCode().value(), "HTTP_ERROR", "Não foi possível atender à solicitação HTTP."), request);
        }
        log.error("Erro inesperado ao processar a solicitação.", exception);
        return response(new ApiError(500, "INTERNAL_ERROR", "Erro interno do servidor."), request);
    }
    public static Object response(ApiError error, HttpServletRequest request) {
        if (path(request).startsWith("/api/") || path(request).equals("/openapi.json")) return ResponseEntity.status(error.status()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(error.body());
        var view = new ModelAndView("error");
        view.setStatus(org.springframework.http.HttpStatusCode.valueOf(error.status()));
        view.addObject("errorStatus", error.status()); view.addObject("errorMessage", error.getMessage()); return view;
    }
    public static String path(HttpServletRequest request) {
        Object original = request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI);
        String uri = original instanceof String value ? value : request.getRequestURI();
        return uri.startsWith(request.getContextPath()) ? uri.substring(request.getContextPath().length()) : uri;
    }
}
