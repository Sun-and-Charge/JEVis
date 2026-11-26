# MSCONS import

The DataCollector parser `MSCONSParser` (`DataCollector/MSCONSParser`) reads EDIFACT MSCONS consumption messages, e.g.
15-minute load profiles of a metering point operator, delivered by the Email, FTP or sFTP driver.

## Setup

- **MSCONS Parser** below the data source; it remembers the sender, recipient and creation date of the last imported
  file (attribute "File Created Date"). A file that is not newer than the last one is skipped; the other files of the
  same run are still imported.
- **MSCONS Data Point** per series:
  - *Messlukation*: the location (LOC+172), e.g. `DE0001234567890000000000000000001`. For a location with several
    registers, add the OBIS code after a bar: `DE0001234567890000000000000000001|1-1:1.29.0` (active energy import) or
    `…|1-1:2.29.0` (export). Without a register all quantities of the location go into the target.
  - *Target*: the raw data row.
  - *Date Time*: `START` or `END` — which end of the interval (DTM+163 / DTM+164) becomes the time stamp.

## What is read

| Segment | Use |
|---|---|
| `UNA` | release character `?` (escaped `'`, `:` and `+` are kept) |
| `UNB`, `UNH`, `BGM`, `NAD` | header; messages other than MSCONS are rejected |
| `LOC+172` | location |
| `PIA+5` | register (OBIS code) of the following quantities |
| `QTY+220` / `QTY+67` / `QTY+201` | true value / substitute value / proposed value; other qualifiers (e.g. `20`, not usable) are skipped |
| `DTM+163` / `DTM+164` | interval start / end, format 303 with the UTC offset (e.g. `202611010000?+01`) |

Decimal commas in quantities are accepted. Time stamps keep their UTC offset, so the change to and from summer time
needs no special handling.

## Not covered yet

Status segments (`STS`) per value, corrections that replace values of an earlier message (versions), and other
EDIFACT message types (e.g. UTILMD). Tests: `MsconsParserTest` (sample file and a message with two registers,
substitute values and an escaped apostrophe).
