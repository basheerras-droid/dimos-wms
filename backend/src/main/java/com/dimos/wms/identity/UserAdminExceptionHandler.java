package com.dimos.wms.identity;

import java.util.List;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Same JSON error shape as the rest of the API, for the user-administration endpoints. */
@RestControllerAdvice(assignableTypes = UserAdminController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class UserAdminExceptionHandler {

    record ApiError(String code, String message, List<String> details, String correlationId) {
    }

    @ExceptionHandler(UserAdminException.class)
    ResponseEntity<ApiError> handle(UserAdminException ex) {
        return ResponseEntity.status(ex.status)
                .body(new ApiError(ex.code, ex.getMessage(), ex.details, MDC.get("correlationId")));
    }
}
