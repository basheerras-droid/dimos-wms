package com.dimos.wms.identity;

import java.util.List;
import org.springframework.http.HttpStatus;

/** A user-administration rule was broken (shown to the administrator, not a bug). */
class UserAdminException extends RuntimeException {

    final HttpStatus status;
    final String code;
    final List<String> details;

    UserAdminException(HttpStatus status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    /** One field is wrong; the screen shows the text next to that field ("field: message"). */
    static UserAdminException invalid(String field, String message) {
        return new UserAdminException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request is not valid.",
                List.of(field + ": " + message));
    }

    static UserAdminException notFound() {
        return new UserAdminException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found.", List.of());
    }

    static UserAdminException stale() {
        return new UserAdminException(HttpStatus.CONFLICT, "STALE_VERSION",
                "This record was changed by someone else - reload", List.of());
    }

    static UserAdminException notAllowed(String message) {
        return new UserAdminException(HttpStatus.UNPROCESSABLE_CONTENT, "NOT_ALLOWED", message, List.of());
    }
}
