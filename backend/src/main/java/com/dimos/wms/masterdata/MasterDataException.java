package com.dimos.wms.masterdata;

import java.util.List;
import org.springframework.http.HttpStatus;

/** A business-rule problem that is shown to the user (not a bug). */
class MasterDataException extends RuntimeException {

    final HttpStatus status;
    final String code;
    final List<String> details;

    MasterDataException(HttpStatus status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    /** 400 - one field is wrong. The UI shows the text next to that field ("field: message"). */
    static MasterDataException invalid(String field, String message) {
        return new MasterDataException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request is not valid.",
                List.of(field + ": " + message));
    }

    static MasterDataException notFound(String what) {
        return new MasterDataException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found.", List.of());
    }

    static MasterDataException stale() {
        return new MasterDataException(HttpStatus.CONFLICT, "STALE_VERSION",
                "This record was changed by someone else - reload", List.of());
    }

    static MasterDataException inUse() {
        return new MasterDataException(HttpStatus.CONFLICT, "IN_USE",
                "This record is still used by other records and cannot be deleted.", List.of());
    }

    static MasterDataException forbidden(String message) {
        return new MasterDataException(HttpStatus.UNPROCESSABLE_CONTENT, "NOT_ALLOWED", message, List.of());
    }
}
