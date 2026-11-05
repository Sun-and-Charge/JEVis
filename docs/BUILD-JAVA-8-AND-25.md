# Building with Java 8 and Java 25 side by side

JEVis needs two JDKs to build everything:

| Part | Java | Why |
|------|------|-----|
| Maven itself, web service, services (JECalc, JEReport, ...) | **25** | The web service uses Jersey 3.1 (Java 11+) and runs on Java 25 |
| JECommons, JEAPI, JEAPI-WS (shared with the Control Center) | compiled by Java 25 with `--release 8` | They must stay loadable by the Control Center |
| Control Center (JEVisControlCenter) | **8 with JavaFX** | It still uses JavaFX 8, which is only included in "FX" builds of Java 8 |

Maven runs on Java 25 (`JAVA_HOME`) and uses a **toolchain** to compile and test the Control Center with
Java 8 (`jdkToolchain` in `JEVisControlCenter/pom.xml`). Both JDKs are installed side by side; nothing
needs to be switched between builds.

A server that only runs JEVis does not need Java 8: it builds without the Control Center (see
[Server without Java 8](#server-without-java-8)).

## 1. Install Java 25 (Linux)

Azul Zulu 25 from the Azul package repository (Debian/Ubuntu):

```bash
sudo apt install gnupg ca-certificates curl
curl -s https://repos.azul.com/azul-repo.key | sudo gpg --dearmor -o /usr/share/keyrings/azul.gpg
echo "deb [signed-by=/usr/share/keyrings/azul.gpg] https://repos.azul.com/zulu/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/zulu.list
sudo apt update
sudo apt install zulu25-jdk
```

The JDK is installed to `/usr/lib/jvm/zulu25`. Any other Java 25 JDK works as well; then adjust the paths below.

## 2. Install Java 8 with JavaFX (Linux)

The Control Center needs a Java 8 build **with JavaFX** ("FX"), e.g. Azul Zulu 8 FX or BellSoft Liberica 8
Full. Download the `.tar.gz` of "Zulu 8, JDK FX, Linux x86 64-bit" from
<https://www.azul.com/downloads/?version=java-8-lts&os=linux&package=jdk-fx> and unpack it next to the other
JDKs, without putting it into the `PATH`:

```bash
sudo tar -xzf zulu8.*-ca-fx-jdk8.*-linux_x64.tar.gz -C /usr/lib/jvm/
sudo ln -sfn /usr/lib/jvm/zulu8.*-ca-fx-jdk8.*-linux_x64 /usr/lib/jvm/zulu8-fx
/usr/lib/jvm/zulu8-fx/bin/java -version          # must say 1.8.0_...
ls /usr/lib/jvm/zulu8-fx/jre/lib/ext/jfxrt.jar   # JavaFX must be there
```

The symlink keeps the toolchain path stable when Java 8 is updated later.

## 3. Make Java 25 the default

`JAVA_HOME` (used by Maven) and `java` in the `PATH` must be Java 25. For the user that builds and runs JEVis,
`etc/jevis.env` (from `var/templates/jevis.env`) does this already:

```bash
JAVA_HOME=/usr/lib/jvm/zulu25
[ -x "${JAVA_HOME}/bin/java" ] && PATH=${JAVA_HOME}/bin:$PATH
```

For other users, or without `jevis.env`, put the same two lines into `~/.profile`. Check:

```bash
java -version     # openjdk version "25..."
mvn -v            # "Java version: 25..."
```

Maven 3.9 or newer is required.

## 4. Register Java 8 as Maven toolchain

Create `~/.m2/toolchains.xml` for the user that builds (template: [toolchains-example.xml](toolchains-example.xml)):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
    <toolchain>
        <type>jdk</type>
        <provides>
            <version>1.8</version>
            <vendor>zulu</vendor>
        </provides>
        <configuration>
            <jdkHome>/usr/lib/jvm/zulu8-fx</jdkHome>
        </configuration>
    </toolchain>
</toolchains>
```

The Control Center asks only for `version` 1.8, so the vendor entry is informational. With the file in
`~/.m2/`, no `-t` option is needed. A file in another place is passed with
`mvn -t /path/to/toolchains.xml ...`.

## 5. Build

```bash
cd ${JEVIS_HOME}
mvn clean install
```

This builds all modules: the server modules with Java 25, the Control Center with the Java 8 toolchain
(compiler and tests). Results:

- web service: `JEWebService/target/JEWebservice-<version>-jar-with-dependencies.jar` (run with Java 25)
- Control Center: `JEVisControlCenter/target/JEVisControlCenter-<version>-jar-with-dependencies.jar`
  (run with Java 8 FX, e.g. `/usr/lib/jvm/zulu8-fx/bin/java -jar ...`)

The database migrations (`mysqldb`) are not part of the normal build. Apply them separately, see the README.

### Check that both JDKs are used

```bash
mvn -q clean verify -pl JEVisControlCenter -am
# class file major version: 69 = Java 25, 52 = Java 8
od -An -j7 -N1 -tu1 JEWebService/target/classes/org/jevis/rest/Main.class          # after building the web service: 69
od -An -j7 -N1 -tu1 JEVisControlCenter/target/classes/org/jevis/jeconfig/JEConfig.class   # 52
```

## Server without Java 8

A server that only runs the web service and the services does not need the Control Center and therefore
no Java 8 and no toolchain:

```bash
mvn clean install -pl '!JEVisControlCenter'
```

`install.sh` builds this way and checks that Java 25 is used.

## Windows (developer machine)

The same setup works on Windows, e.g. with the Azul MSI installers:

- Zulu 25 JDK, e.g. `C:\Program Files\Zulu\zulu-25`, set as `JAVA_HOME` and first in `PATH`
- Zulu 8 FX JDK, e.g. `C:\Program Files\Zulu\zulu-8`, **not** in `PATH`
- `%USERPROFILE%\.m2\toolchains.xml` as above with `<jdkHome>C:\Program Files\Zulu\zulu-8</jdkHome>`

In IntelliJ IDEA, set the project SDK to Java 25 and the module SDK of `JEVisControlCenter` to Java 8 FX
(Project Structure > Modules > JEVisControlCenter > Dependencies > Module SDK). Maven builds started from
the IDE use the toolchain anyway.

## Troubleshooting

| Message | Cause | Fix |
|---------|-------|-----|
| `Cannot find matching toolchain definitions for the following toolchain types: jdk [ version='1.8' ]` | No toolchain for Java 8 | Create `~/.m2/toolchains.xml` (step 4) or build with `-pl '!JEVisControlCenter'` |
| `package javafx... does not exist` in the Control Center | The Java 8 of the toolchain has no JavaFX | Use an "FX" build of Java 8 (step 2) |
| `invalid target release: 25` / `release version 25 not supported` | Maven runs on an older JDK | Set `JAVA_HOME` to Java 25 (step 3) |
| `UnsupportedClassVersionError ... class file version 69.0` when starting the web service | Started with an old `java` | Start it with Java 25 (`jevis.env`, `bin/jewebservice`) |
| Control Center does not start with Java 25 | It needs JavaFX 8 | Start it with the Java 8 FX JDK |
