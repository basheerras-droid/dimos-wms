package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Check configuration": a read-only report of PASS / WARNING / ERROR per rule, with the records concerned. */
@RestController
@RequestMapping("/api/masterdata/validation")
class ValidationController {

    private final ConfigurationValidator validator;

    ValidationController(ConfigurationValidator validator) {
        this.validator = validator;
    }

    @GetMapping
    @PreAuthorize(MasterDataController.VIEW)
    ConfigurationValidator.Report check(@AuthenticationPrincipal WmsUserPrincipal who) {
        return validator.check(who.siteId());
    }
}
