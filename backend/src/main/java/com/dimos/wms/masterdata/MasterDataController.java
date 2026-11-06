package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The common REST endpoints (get one, create, update, delete). Each entity adds its own list endpoint. */
abstract class MasterDataController<E extends SiteEntity, Q extends VersionedRequest, R> {

    static final String VIEW = "hasAuthority('MASTERDATA_VIEW')";
    static final String EDIT = "hasAuthority('MASTERDATA_EDIT')";

    protected abstract MasterDataService<E, Q, R> service();

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    R get(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service().get(id, who);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    R create(@Valid @RequestBody Q request, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service().create(request, who);
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    R update(@PathVariable Long id, @Valid @RequestBody Q request, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service().update(id, request, who);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(EDIT)
    void delete(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        service().delete(id, who);
    }
}
