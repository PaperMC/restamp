# restamp

Restamp is a library for applying access transformers to java source code files.

## Overview

Restamp can be used to apply [access transformers](https://github.com/MinecraftForge/AccessTransformers) to closed/forked projects, enabling
developers to access fields, methods and classes in the project that were previously not accessible.

## Installation & Usage

> For both library and cli usage it is important that a full classpath of the project is supplied to the project.
> This includes a jar of the project itself if only a subset of its classes are read/parsed by restamp (which is almost always the case).

### As a library
#### Gradle (Kotlin DSL)
```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.restamp:restamp:1.1.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}
```
#### Maven
```xml
<repository>
    <id>papermc</id>
    <url>https://repo.papermc.io/repository/maven-public/</url>
</repository>
```
```xml
<dependency>
    <groupId>io.papermc.restamp</groupId>
    <artifactId>restamp</artifactId>
    <version>1.1.0</version>
</dependency>
```

You may use restamp by first constructing a `RestampContextConfiguration` via its builder:

```java
RestampContextConfiguration.builder()
    .accessTransformers(Path.of("/path/to/the/at/file"))
    [...]
    .build();
```

The configuration may then be parsed into the **inputs** to restamp via `RestampInput.parseFrom(contextConfiguration)`.
This process parses the java source files as specified in the context configuration. As such it is not a cheap process and
should be called as few times as possible.

After constructing inputs, **restamp** can be executed using `Restamp.run(inputs)`, yielding back the changeset of all applied access transformers
for further usage by your jvm-based project.

### As a CLI

You can download the cli from the GitHub Releases page or you can build it with `./gradlew build` which produces the cli in
`restamp-cli/build/libs/restamp-cli-*-final.jar`.

More information on the CLI's expected parameters can be found via the `--help` flag.
A sample execution may look like this:

```shell
java -jar restamp.jar \
  -cp "lib/guava.jar;lib/joml.jar;[...]" \
  --source-path src/main/java \
  -at at.at
```

which applies all access transformers found in the `at.at` file to the relevant sources found under `src/main/java` while using
all classes found in the jars supposed to `-cp` as a semicolon separated array.
