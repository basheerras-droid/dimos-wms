package com.dimos.wms.identity;

import java.io.Serializable;
import java.util.List;

/** What the browser session remembers about the logged-in user (never the password hash). */
public record WmsUserPrincipal(Long userId, Long siteId, String username, String displayName,
                               List<String> roles, List<String> permissions,
                               boolean mustChangePassword) implements Serializable {

    WmsUserPrincipal withMustChangePassword(boolean value) {
        return new WmsUserPrincipal(userId, siteId, username, displayName, roles, permissions, value);
    }
}
