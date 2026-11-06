package com.dimos.wms.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** A place where the WMS is used: a PC, handheld, HMI or kiosk. A browser becomes this workstation by registering. */
@Entity
@Table(name = "workstation")
class Workstation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "workstation_seq")
    @SequenceGenerator(name = "workstation_seq", sequenceName = "workstation_seq", allocationSize = 1)
    private Long id;

    @Column(name = "site_id", nullable = false, updatable = false)
    private Long siteId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "workstation_type", nullable = false)
    private String type;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "workstation_group_id")
    private Long workstationGroupId;

    @Column(name = "host_name")
    private String hostName;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "check_ip", nullable = false)
    private boolean checkIp;

    @Column(name = "default_printer")
    private String defaultPrinter;

    @Column(nullable = false)
    private boolean enabled = true;

    /** SHA-256 (hex) of the token in the registered browser's cookie; null = no browser registered. */
    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "registered_by")
    private String registeredBy;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected Workstation() {
    }

    static Workstation create(Long siteId, String code, String actor) {
        Workstation w = new Workstation();
        Instant now = Instant.now();
        w.siteId = siteId;
        w.code = code;
        w.createdAt = now;
        w.createdBy = actor;
        w.updatedAt = now;
        w.updatedBy = actor;
        return w;
    }

    void update(String name, String type, Long locationId, Long workstationGroupId, String hostName, String ipAddress,
                boolean checkIp, String defaultPrinter, boolean enabled, String actor) {
        this.name = name;
        this.type = type;
        this.locationId = locationId;
        this.workstationGroupId = workstationGroupId;
        this.hostName = hostName;
        this.ipAddress = ipAddress;
        this.checkIp = checkIp;
        this.defaultPrinter = defaultPrinter;
        this.enabled = enabled;
        touch(actor);
    }

    void register(String tokenHash, String actor) {
        this.tokenHash = tokenHash;
        this.registeredAt = Instant.now();
        this.registeredBy = actor;
        touch(actor);
    }

    void clearRegistration(String actor) {
        this.tokenHash = null;
        this.registeredAt = null;
        this.registeredBy = null;
        touch(actor);
    }

    private void touch(String actor) {
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    Long getId() { return id; }
    Long getSiteId() { return siteId; }
    String getCode() { return code; }
    String getName() { return name; }
    String getType() { return type; }
    Long getLocationId() { return locationId; }
    Long getWorkstationGroupId() { return workstationGroupId; }
    String getHostName() { return hostName; }
    String getIpAddress() { return ipAddress; }
    boolean isCheckIp() { return checkIp; }
    String getDefaultPrinter() { return defaultPrinter; }
    boolean isEnabled() { return enabled; }
    String getTokenHash() { return tokenHash; }
    Instant getRegisteredAt() { return registeredAt; }
    String getRegisteredBy() { return registeredBy; }
    long getVersion() { return version; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }
}
