JEVis Control Center
====================

JavaFX desktop application for the JEVis system (formerly JEConfig). It is used to configure the
JEVis object tree, data sources and services, and to analyse data with charts, dashboards, maps,
meters, reports and alarms.

Build and start:

```bash
mvn clean install
java -jar target/JEVisControlCenter-<version>-jar-with-dependencies.jar
```

Requires Java 8 with JavaFX.

Licenses: **Help > Licenses** shows the JEVis license (GPLv3) and the licenses of all third-party
libraries and bundled resources. The library list is generated during the build by the
license-maven-plugin (`src/license/`). Resources that are not Maven dependencies, such as icons and
fonts, are listed in `src/main/resources/jevis-licenses/bundled-assets.tsv`. Please add an entry there
when you add such a resource.
