plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))
tasks.shadowJar { archiveClassifier.set("final"); mergeServiceFiles() }

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openrewrite:rewrite-java-17:8.7.4")
    implementation("org.cadixdev:at:0.1.0-rc1")
    implementation("org.apache.logging.log4j:log4j-core:3.0.0-alpha1")
    implementation("org.slf4j:slf4j-api:2.0.9")
}
