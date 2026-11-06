package com.dimos.wms.masterdata;

/** One row of an import file is wrong. The message is shown to the user next to that row. */
class ImportRowException extends RuntimeException {

    ImportRowException(String message) {
        super(message);
    }
}
