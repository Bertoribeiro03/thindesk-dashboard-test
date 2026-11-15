package com.pfc.web;

import com.pfc.api.ApiError;
import com.pfc.api.ErrorHandlers;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorPageController implements ErrorController {
    @RequestMapping("/error") public Object error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Number number ? number.intValue() : 500;
        String code = status == 404 ? "NOT_FOUND" : status >= 500 ? "INTERNAL_ERROR" : "HTTP_ERROR";
        String message = status == 404 ? "Recurso não encontrado." : status >= 500 ? "Erro interno do servidor." : "Não foi possível atender à solicitação.";
        return ErrorHandlers.response(new ApiError(status, code, message), request);
    }
}
