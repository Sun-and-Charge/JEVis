# JEVis

JEVis is an open source system for storing, monitoring and analysing energy and operational data.
It collects measurements from meters, sensors and other systems and processes them into clean data
for charts, dashboards, reports and alarms.

This repository is maintained by [Sun and Charge Germany GmbH](https://github.com/Sun-and-Charge).
It is a fork of the OpenJEVis project, originally developed by Envidatec GmbH. See [NOTICE](NOTICE).

## Features

- Reliable data acquisition from many sources, such as FTP/sFTP, HTTP, SOAP, Modbus, OPC UA,
  e-mail, SQL and DWD weather data, with optional on-demand OpenVPN tunnels
- Data processing: cleaning, limits, gap filling, aggregation and calculations
- Visualisation in the JEVis Control Center: charts, dashboards, maps and meter management
- Back-end services for alarms, reports, notifications and status monitoring
- REST web service and Java API for integration with other systems
- Multilingual user interface (English, German, Russian, Ukrainian, Thai and Arabic)

## Modules

| Module | Description |
|---|---|
| `JEAPI` | Java API of the JEVis system |
| `JEAPI-WS` | Client implementation of the API for the JEVis web service |
| `JECommons` | Common functions shared by all modules, including i18n resources |
| `JEWebService` | REST web service (server) |
| `JEVisControlCenter` | JavaFX desktop application for configuration and analysis |
| `DataCollector` | Data collection service (`JEDataCollector`) and its data source drivers and parsers |
| `JEDataProcessor` | Data cleaning and processing service |
| `JECalc` | Calculation service |
| `JEReport` | Report service |
| `JEAlarm` | Alarm service |
| `JEStatus` | Status and monitoring service |
| `Notifier` | Notification service |
| `mysqldb` | Database schema and Flyway migrations (profile `jevisserver`) |

## Requirements

- Java 8 with JavaFX (for example Zulu or Liberica "FX" builds)
- Maven 3.6+
- MySQL (5.7 or newer recommended)
- LibreOffice 5+ (only for reports)
- A Java keystore for the TLS connection of the web service

## Build

```bash
mvn clean install
```

The Control Center is built as an executable jar with all dependencies:

```bash
java -jar JEVisControlCenter/target/JEVisControlCenter-<version>-jar-with-dependencies.jar
```

To build the server including the database migrations, use the `jevisserver` profile:

```bash
mvn clean install -P jevisserver
```

## Installation (server)

`install.sh` sets up a JEVis server on Linux. It builds the project, creates the configuration files
from `var/templates` in `etc/` and optionally installs the web service as a systemd service.
The service start scripts are in `bin/`.

## License

JEVis is free software licensed under the
[GNU General Public License version 3](https://www.gnu.org/licenses/gpl-3.0.html) (GPL-3.0-only).
See [LICENSE](LICENSE) and [NOTICE](NOTICE).

The copyright notices of the original authors in the source files are retained, as the GPL requires.
Third-party libraries and bundled resources have their own licenses. The Control Center lists all of
them under **Help > Licenses**. The library list is generated from the Maven dependencies during every
build. Open licensing questions are tracked in [docs/LICENSE-AUDIT.md](docs/LICENSE-AUDIT.md).

## Contributing and support

Please report bugs and feature requests via
[GitHub Issues](https://github.com/Sun-and-Charge/JEVis/issues). Pull requests are welcome.
New source files should carry the GPLv3 header from `JECommons/licenseheader.txt`.
