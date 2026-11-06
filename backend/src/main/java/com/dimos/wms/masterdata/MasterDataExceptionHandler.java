package com.dimos.wms.masterdata;

import java.util.List;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns master-data problems into the same JSON error shape as the rest of the API. */
@RestControllerAdvice(basePackages = "com.dimos.wms.masterdata")
@Order(Ordered.HIGHEST_PRECEDENCE)
class MasterDataExceptionHandler {

    record ApiError(String code, String message, List<String> details, String correlationId) {
    }

    @ExceptionHandler(MasterDataException.class)
    ResponseEntity<ApiError> business(MasterDataException ex) {
        return ResponseEntity.status(ex.status)
                .body(new ApiError(ex.code, ex.getMessage(), ex.details, MDC.get("correlationId")));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooBig(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return business(new MasterDataException(org.springframework.http.HttpStatus.CONTENT_TOO_LARGE,
                "FILE_TOO_LARGE", "The file is too large (maximum 10 MB).", List.of()));
    }

    /** Two people saved the same record at the same moment. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> stale(ObjectOptimisticLockingFailureException ex) {
        return business(MasterDataException.stale());
    }

    /** A database rule (unique code, foreign key) stopped the change although the checks above did not. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex) {
        return business(new MasterDataException(org.springframework.http.HttpStatus.CONFLICT, "CONFLICT",
                "The change conflicts with existing data (duplicate code or record in use).", List.of()));
    }
}
