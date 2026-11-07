package com.dimos.wms.project;

/** The project loader refused a request (shown to the administrator, not a bug). */
public class ProjectException extends RuntimeException {

    private final int status;

    ProjectException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
