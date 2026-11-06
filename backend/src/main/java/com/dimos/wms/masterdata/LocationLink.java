package com.dimos.wms.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** "From location A a handling unit can move to location B" - the graph that routing searches later. */
@Entity
@Table(name = "location_link")
class LocationLink extends SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "location_link_seq")
    @SequenceGenerator(name = "location_link_seq", sequenceName = "location_link_seq", allocationSize = 1)
    Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_location_id", nullable = false)
    Location fromLocation;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "to_location_id", nullable = false)
    Location toLocation;

    /** Empty = the link is valid for all handling-unit types. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hu_type_id")
    HuType huType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "equipment_id")
    Equipment equipment;

    @Column(nullable = false)
    int cost = 1;

    @Column(nullable = false)
    boolean enabled = true;

    @Override
    Long getId() {
        return id;
    }
}

interface LocationLinkRepository extends SiteRepository<LocationLink> {
}
