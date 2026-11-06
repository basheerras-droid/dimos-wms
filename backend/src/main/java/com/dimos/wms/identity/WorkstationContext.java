package com.dimos.wms.identity;

import java.util.Set;

/**
 * Which workstation a request comes from, and what that means for the rights of the user.
 *
 * <ul>
 *   <li>OFFICE: the browser is not registered (or its registration is no longer valid). No restriction.</li>
 *   <li>REGISTERED: a registered browser; if its group has a list of allowed permissions, only those apply.</li>
 *   <li>DISABLED: the workstation is switched off; nothing is allowed (except what a super administrator needs to fix it).</li>
 *   <li>IP_MISMATCH: registered with an IP check, but the request comes from another address; treated like OFFICE.</li>
 * </ul>
 *
 * {@code restriction} is the set of allowed permissions, or null when nothing is restricted.
 */
record WorkstationContext(Mode mode, boolean staleCookie, Long id, String code, String name, String type,
                          String groupCode, String locationCode, Set<String> restriction, Long groupId) {

    enum Mode { OFFICE, REGISTERED, DISABLED, IP_MISMATCH }

    static final WorkstationContext OFFICE = new WorkstationContext(Mode.OFFICE, false, null, null, null, null, null, null, null, null);

    static WorkstationContext officeWithStaleCookie() {
        return new WorkstationContext(Mode.OFFICE, true, null, null, null, null, null, null, null, null);
    }

    /** True when the rights of the user are cut down at this workstation. */
    boolean restricts() {
        return mode == Mode.DISABLED || (mode == Mode.REGISTERED && restriction != null);
    }
}
