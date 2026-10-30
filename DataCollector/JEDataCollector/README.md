JEDataCollector
===============

JEVis automatic data collector

- [VPN on demand](docs/VPN.md) – OpenVPN-Tunnel nur für die Dauer des Auslesens aufbauen

## Treiber

Die Datenquellen- und Parser-Treiber sind fest im JEDataCollector enthalten und in
[`BuiltInDrivers`](src/main/java/org/jevis/jedatacollector/BuiltInDrivers.java) der jeweiligen JEVis-Klasse zugeordnet.
Unterklassen (z. B. eine eigene Klasse unterhalb von `HTTP Server`) verwenden den Treiber der Elternklasse.
Treiberverzeichnisse und Treiberobjekte unter dem Dienstobjekt werden nicht mehr benötigt. Bestehende Objekte entfernt
die Migration `V11__Remove_DataCollector_Driver_Objects.sql` (Papierkorb). Für den Dienst gelten nur noch die
Attribute des **JEDataCollector**-Objekts.

Einen neuen Treiber als Modul unter `DataCollector/` anlegen, als Abhängigkeit in `pom.xml` eintragen und in
`BuiltInDrivers` registrieren. Aktivierte Datenquellen ohne passenden Treiber erscheinen einmal als Warnung im Log.
