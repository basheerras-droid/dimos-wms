package com.dimos.wms.masterdata;

/** A create/update request. {@code version} is the version the user saw; it is required when updating. */
interface VersionedRequest {
    Long version();
}
