# Class icons

The icons of the JEVis classes in `var/classes/**.png` (object tree and dialogs of the Control Center) are generated
here: a white [Material Symbol](https://github.com/google/material-design-icons) (Apache License 2.0) on a rounded
tile in the colour of the area, folders (`… Directory`) as a folder silhouette in that colour with the symbol of
their content inside. Filled symbols are used where they exist, since they keep their weight at the 16–20 px of the
tree.

| Area | Colour | Examples |
|---|---|---|
| Organisation, sites, general folders | `#2f4a78` | Organisation, Standort |
| Raw data / clean data | `#2563eb` / `#0891b2` | Data (database), Clean Data (funnel) |
| Other data kinds | `#4f46e5` | Math, Forecast, String, User Data, Base Data |
| Data sources | `#475569` | servers, channels, data points, parsers |
| Calculations | `#7c3aed` | Calculation, Input, Output, templates |
| Analyses, dashboards | `#0284c7` | Analysis, Dashboard |
| Reports | `#be185d` | Report, Periodic Report |
| Alarms, notifications | `#dc2626` | Alarm Configuration, Notification |
| Finance | `#d97706` | contracts, invoices, accounting |
| ISO 50001 | `#16a34a` | action plans, nonconformities, legal register, maintenance, appointments, notes |
| Meters, equipment | `#0d9488` | measurement instruments, equipment |
| System | `#64748b` | users, groups, services, plugins |

## Change or add an icon

1. Edit the mapping in `mapping.py` (class name → symbol name and area; plugins in `PLUGINS`; generic rules in
   `rule()`); symbol names as on [fonts.google.com/icons](https://fonts.google.com/icons).
2. Generate (Java 25 with OpenJFX on the class path, e.g. the Control Center jar):

```sh
python3 tools/class-icons/mapping.py var/classes /tmp/icons/mapping.tsv
tools/class-icons/fetch-glyphs.sh /tmp/icons/mapping.tsv /tmp/icons
javac -cp JEVisControlCenter.jar -d /tmp/icons tools/class-icons/IconArt.java tools/class-icons/IconGenerate.java
java -cp "/tmp/icons:JEVisControlCenter.jar" IconGenerate /tmp/icons/outlined /tmp/icons/filled /tmp/icons/mapping.tsv /tmp/icons/out
```

3. Copy `/tmp/icons/out/**.png` over `var/classes`. The web service rebuilds its icon zip when an icon is newer
   than the zip, and the Control Center fetches the icons once per session.
