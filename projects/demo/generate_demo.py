#!/usr/bin/env python3
"""
Generates the CSV files of the DIMOS demo plant (projects/demo/*.csv).

The demo plant is generic and invented by DIMOS - it contains no customer data. Run it from this folder:

    python generate_demo.py

The CSV files are committed, so the generator is only needed to change the layout (e.g. more aisles).

Layout (all positions in mm, x to the right, y downwards, level_no = floor / rack level):
  INBOUND   conveyor line IN-01..IN-06 (level 1) -> transfer unit XFER-01
  STORAGE   3 aisles A01..A03, each with a pick-up point (A0n-IN), a drop-off point (A0n-OUT) and a rack of
            2 sides x 74 columns x 12 levels = 1,776 bins per aisle (5,328 in total, the size of the INL high bay);
            one stacker crane per aisle moves units between the points and the bins
  OUTBOUND  conveyor OC-01 -> hoist H1 (stops on levels 1, 2, 3) -> stations OUT-01 (level 2) and OUT-02 (level 3);
            an AGV path AGV-01..AGV-04 leaves station OUT-01
"""
import csv
import os

HERE = os.path.dirname(os.path.abspath(__file__))

AISLES = ["A01", "A02", "A03"]
SIDES = ["L", "R"]
COLUMNS = 74
LEVELS = 12
COLUMN_PITCH = 1100      # mm between two bins in a row
LEVEL_PITCH = 1500       # mm between two rack levels
RACK_X0 = 3000           # x of column 1
AISLE_PITCH = 7000       # y distance between two aisles
AISLE_Y0 = 10000
SIDE_OFFSET = 3500       # y distance between the left and the right bin row of one aisle


def write(name, header, rows):
    with open(os.path.join(HERE, name), "w", newline="", encoding="ascii") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(header)
        w.writerows(rows)
    print("%-26s %6d rows" % (name, len(rows)))


def aisle_y(aisle):
    return AISLE_Y0 + AISLES.index(aisle) * AISLE_PITCH


def main():
    # ---- areas, types -----------------------------------------------------------------------------------
    write("areas.csv", ["code", "name", "area_type"], [
        ["INBOUND", "Inbound", "INBOUND"],
        ["STORAGE", "High-bay storage", "STORAGE"],
        ["OUTBOUND", "Outbound", "OUTBOUND"],
    ])
    write("location-types.csv", ["code", "name", "is_storage", "is_transfer", "is_workstation", "capacity_hu"], [
        ["RACK_BIN", "Rack bin", "TRUE", "FALSE", "FALSE", 1],
        ["CONV_POS", "Conveyor position", "FALSE", "FALSE", "FALSE", 1],
        ["TRANSFER", "Transfer position", "FALSE", "TRUE", "FALSE", 1],
        ["CRANE_PD", "Stacker crane pick-up / drop-off", "FALSE", "TRUE", "FALSE", 1],
        ["HOIST_STOP", "Hoist stop", "FALSE", "TRUE", "FALSE", 1],
        ["STATION", "Outbound station", "FALSE", "FALSE", "TRUE", 1],
        ["AGV_NODE", "AGV path point", "FALSE", "TRUE", "FALSE", 1],
    ])
    write("hu-types.csv", ["code", "name", "length_mm", "width_mm", "height_mm", "tare_kg", "max_gross_kg", "is_container"], [
        ["PALLET", "Pallet 1200 x 800", 1200, 800, 144, 25, 1000, "TRUE"],
        ["ULD", "Air cargo container (PMC pallet)", 3175, 2438, 1626, 120, 6800, "TRUE"],
        ["CART", "Cart / trolley", 1200, 800, 1700, 60, 500, "TRUE"],
        ["BIN", "Plastic bin 600 x 400", 600, 400, 320, 2, 50, "FALSE"],
    ])
    write("hu-type-nesting.csv", ["inner_hu_type_code", "outer_hu_type_code", "max_qty"], [
        ["BIN", "PALLET", 24],
        ["BIN", "CART", 6],
        ["BIN", "ULD", 40],
        ["PALLET", "ULD", 2],
    ])
    all_hu = ["PALLET", "ULD", "CART", "BIN"]
    accepts = {
        "RACK_BIN": ["PALLET", "BIN"],
        "CONV_POS": all_hu,
        "TRANSFER": all_hu,
        "CRANE_PD": ["PALLET", "BIN"],
        "HOIST_STOP": all_hu,
        "STATION": all_hu,
        "AGV_NODE": ["PALLET", "CART", "BIN"],
    }
    write("location-type-hu-types.csv", ["location_type_code", "hu_type_code"],
          [[t, h] for t, hs in accepts.items() for h in hs])

    # ---- locations --------------------------------------------------------------------------------------
    header = ["code", "name", "area_code", "location_type_code", "x_mm", "y_mm", "z_mm", "level_no", "aisle", "side",
              "column_no", "row_no", "max_weight_kg"]
    locations = []

    def loc(code, name, area, ltype, x, y, level, **kw):
        locations.append([code, name, area, ltype, x, y, (level - 1) * LEVEL_PITCH, level, kw.get("aisle", ""),
                          kw.get("side", ""), kw.get("column", ""), kw.get("row", ""), kw.get("weight", "")])

    for i in range(6):
        loc("IN-%02d" % (i + 1), "Inbound conveyor %d" % (i + 1), "INBOUND", "CONV_POS", 1000 + i * 1200, 2000, 1)
    loc("XFER-01", "Distribution transfer", "INBOUND", "TRANSFER", 8500, 2000, 1)
    for a in AISLES:
        y = aisle_y(a) + SIDE_OFFSET // 2
        loc(a + "-IN", "Aisle %s pick-up" % a, "STORAGE", "CRANE_PD", 500, y, 1, aisle=a)
        loc(a + "-OUT", "Aisle %s drop-off" % a, "STORAGE", "CRANE_PD", 1700, y, 1, aisle=a)
    loc("OC-01", "Outbound conveyor", "OUTBOUND", "CONV_POS", 6500, 5000, 1)
    for lv in (1, 2, 3):
        loc("H1-L%d" % lv, "Hoist 1 level %d" % lv, "OUTBOUND", "HOIST_STOP", 8500, 5000, lv)
    loc("OUT-01", "Outbound station 1", "OUTBOUND", "STATION", 10000, 5000, 2)
    loc("OUT-02", "Outbound station 2", "OUTBOUND", "STATION", 10000, 5000, 3)
    for n, (x, y) in enumerate([(12000, 5000), (12000, 8000), (15000, 8000), (15000, 5000)], start=1):
        loc("AGV-%02d" % n, "AGV path point %d" % n, "OUTBOUND", "AGV_NODE", x, y, 2)
    bins = {}
    for a in AISLES:
        bins[a] = []
        for s, side in enumerate(SIDES):
            for c in range(1, COLUMNS + 1):
                for lv in range(1, LEVELS + 1):
                    code = "%s-%s-%02d-%02d" % (a, side, c, lv)
                    bins[a].append((code, c))
                    loc(code, "", "STORAGE", "RACK_BIN", RACK_X0 + (c - 1) * COLUMN_PITCH, aisle_y(a) + s * SIDE_OFFSET,
                        lv, aisle=a, side=side, column=c, row=lv, weight=1000)
    write("locations.csv", header, locations)

    # ---- equipment --------------------------------------------------------------------------------------
    equipment = [["CONV-IN", "Inbound conveyor", "CONVEYOR", "INBOUND"],
                 ["XFER-1", "Distribution transfer unit", "TRANSFER", "INBOUND"]]
    for n, a in enumerate(AISLES, start=1):
        equipment.append(["SC-%s" % a, "Stacker crane aisle %s" % a, "STACKER_CRANE", "STORAGE"])
    equipment += [["CONV-OUT", "Outbound conveyor", "CONVEYOR", "OUTBOUND"],
                  ["HOIST-1", "Hoist 1 (levels 1 to 3)", "HOIST", "OUTBOUND"],
                  ["STN-1", "Outbound station 1", "STATION", "OUTBOUND"],
                  ["STN-2", "Outbound station 2", "STATION", "OUTBOUND"],
                  ["AGV-1", "AGV 1", "AGV", "OUTBOUND"]]
    write("equipment.csv", ["code", "name", "equipment_type_code", "area_code"], equipment)

    assign = []
    assign += [["CONV-IN", "IN-%02d" % i] for i in range(1, 7)]
    assign += [["XFER-1", "XFER-01"]]
    for a in AISLES:
        assign += [["SC-" + a, a + "-IN"], ["SC-" + a, a + "-OUT"]] + [["SC-" + a, code] for code, _ in bins[a]]
    assign += [["CONV-OUT", "OC-01"]] + [["HOIST-1", "H1-L%d" % lv] for lv in (1, 2, 3)]
    assign += [["STN-1", "OUT-01"], ["STN-2", "OUT-02"]] + [["AGV-1", "AGV-%02d" % n] for n in range(1, 5)]
    write("equipment-locations.csv", ["equipment_code", "location_code"], assign)

    # ---- links ------------------------------------------------------------------------------------------
    links = []

    def link(a, b, equipment="", cost=1):
        links.append([a, b, "", equipment, cost, "TRUE"])

    for i in range(1, 6):
        link("IN-%02d" % i, "IN-%02d" % (i + 1), "CONV-IN")
    link("IN-06", "XFER-01", "CONV-IN")
    for a in AISLES:
        link("XFER-01", a + "-IN", "XFER-1", 2)
        link(a + "-OUT", "OC-01", "CONV-OUT", 2)
        for code, c in bins[a]:
            cost = 1 + c // 5
            link(a + "-IN", code, "SC-" + a, cost)
            link(code, a + "-OUT", "SC-" + a, cost)
    link("OC-01", "H1-L1", "CONV-OUT")
    for lv in (2, 3):
        link("H1-L1", "H1-L%d" % lv, "HOIST-1", lv)
        link("H1-L%d" % lv, "H1-L1", "HOIST-1", lv)
    link("H1-L2", "OUT-01", "HOIST-1")
    link("H1-L3", "OUT-02", "HOIST-1")
    link("OUT-01", "AGV-01", "AGV-1")
    for n in range(1, 5):
        link("AGV-%02d" % n, "AGV-%02d" % (n % 4 + 1), "AGV-1", 3)
    write("location-links.csv", ["from_location_code", "to_location_code", "hu_type_code", "equipment_code", "cost", "enabled"],
          links)

    # ---- administration: user groups, workstation groups, workstations, parameters ----------------------
    write("user-groups.csv", ["code", "name", "description", "enabled", "roles"], [
        ["DEMO-OPERATORS", "Demo operators", "Day-to-day operation of the demo plant", "TRUE", "OPERATOR"],
        ["DEMO-SUPERVISORS", "Demo supervisors", "Supervise the demo plant and maintain its master data", "TRUE", "OPERATOR;ADMIN"],
        ["DEMO-VIEWERS", "Demo viewers", "Read-only access, e.g. for visitors", "TRUE", "VIEWER"],
    ])
    write("workstation-groups.csv", ["code", "name", "description", "enabled", "allowed_permissions"], [
        ["WSG-OFFICE", "Office PCs", "Office computers: everything a user is entitled to",
         "TRUE", "DASHBOARD_VIEW;MASTERDATA_VIEW;MASTERDATA_EDIT;USER_MANAGE;AUDIT_VIEW;ROLE_MANAGE;WORKSTATION_MANAGE;PARAMETER_VIEW;PARAMETER_EDIT"],
        ["WSG-FLOOR", "Floor terminals", "Terminals and handhelds in the plant: operation only", "TRUE",
         "DASHBOARD_VIEW;MASTERDATA_VIEW"],
    ])
    write("workstations.csv", ["code", "name", "type", "location_code", "workstation_group_code", "host_name", "ip_address",
                               "check_ip", "default_printer", "enabled"], [
        ["WS-OFFICE-01", "Office PC 1", "PC", "", "WSG-OFFICE", "", "", "FALSE", "", "TRUE"],
        ["WS-IN-01", "Inbound terminal", "HMI", "IN-01", "WSG-FLOOR", "", "", "FALSE", "", "TRUE"],
        ["WS-OUT-01", "Outbound station 1 terminal", "HMI", "OUT-01", "WSG-FLOOR", "", "", "FALSE", "", "TRUE"],
        ["WS-OUT-02", "Outbound station 2 terminal", "HMI", "OUT-02", "WSG-FLOOR", "", "", "FALSE", "", "TRUE"],
        ["WS-HH-01", "Handheld scanner 1", "HANDHELD", "", "WSG-FLOOR", "", "", "FALSE", "", "TRUE"],
    ])
    write("parameters.csv", ["parameter", "scope", "scope_code", "value"], [
        ["security.session_timeout", "WS_GROUP", "WSG-FLOOR", "PT8H"],
        ["security.session_timeout", "WORKSTATION", "WS-HH-01", "PT2H"],
    ])


if __name__ == "__main__":
    main()
