package com.dimos.wms.masterdata;

import java.util.List;

/** One page of a list plus the total number of matching rows. */
record PageResult<T>(List<T> items, long total, int page, int size) {
}
