package com.dimos.wms.identity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The complete list of permissions of the product, fixed in code. A new permission is added here AND in a Flyway
 * migration (so roles can be linked to it); a start-up check and a test make sure the two agree.
 */
public final class PermissionCatalogue {

    /** One permission: its code, the module it belongs to (for grouping on the screen) and what it allows. */
    public record Entry(String code, String module, String description) {
    }

    public static final List<Entry> ALL = List.of(
            new Entry("DASHBOARD_VIEW", "dashboard", "View the dashboard"),
            new Entry("MASTERDATA_VIEW", "masterdata", "View master data (areas, locations, equipment, HU types, layout, configuration check)"),
            new Entry("MASTERDATA_EDIT", "masterdata", "Create, change, delete and import master data"),
            new Entry("USER_MANAGE", "administration", "Create and change users and user groups"),
            new Entry("AUDIT_VIEW", "administration", "View the audit log"),
            new Entry("ROLE_MANAGE", "administration", "Create, change and delete custom roles"),
            new Entry("WORKSTATION_MANAGE", "administration", "Manage workstations and workstation groups"),
            new Entry("PARAMETER_VIEW", "administration", "See the system parameters and their effective values"),
            new Entry("PARAMETER_EDIT", "administration", "Change system parameter values"));

    private static final Map<String, Entry> BY_CODE = ALL.stream().collect(Collectors.toMap(Entry::code, Function.identity()));

    private PermissionCatalogue() {
    }

    public static boolean exists(String code) {
        return BY_CODE.containsKey(code);
    }

    public static Entry get(String code) {
        return BY_CODE.get(code);
    }

    /** The permission that lets a super administrator keep managing workstations whatever a workstation group says. */
    public static final String WORKSTATION_MANAGE = "WORKSTATION_MANAGE";
}
