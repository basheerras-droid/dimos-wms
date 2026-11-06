package com.dimos.wms.masterdata;

/** The uploaded file as a whole cannot be used (wrong type, empty, mixed separators, ...). Shown as a file-level message. */
class ImportFileException extends RuntimeException {

    ImportFileException(String message) {
        super(message);
    }
}
