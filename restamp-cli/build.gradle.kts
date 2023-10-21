plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))
tasks.shadowJar { archiveClassifier.set("final"); mergeServiceFiles() }
tasks.assemble { dependsOn(tasks.shadowJar) }
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "io.papermc.restamp.cli.RestampCLI",
            "Specification-Title" to "codebook-cli",
            "Specification-Version" to project.version,
            "Specification-Vendor" to "PaperMC",
        )
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.restamp)
    implementation("info.picocli:picocli:4.7.5")
}

tasks.test {
    useJUnitPlatform()
}
