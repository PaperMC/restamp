plugins {
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))
tasks.shadowJar { archiveClassifier.set("final"); mergeServiceFiles() }

repositories {
    mavenCentral()
}

dependencies {
    api("org.openrewrite:rewrite-java-17:8.7.4")
    api("org.cadixdev:at:0.1.0-rc1")
    implementation("org.apache.logging.log4j:log4j-core:3.0.0-alpha1")
    implementation("org.slf4j:slf4j-api:2.0.9")

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        if (System.getenv()["CI"]?.toBoolean() != true) excludeTags.add("function")
    }
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

publishing {
    repositories {
        maven("https://repo.papermc.io/repository/maven-snapshots/") {
            credentials(PasswordCredentials::class)
            name = "paper"
        }
    }

    publications.create<MavenPublication>("maven") {
        artifactId = "restamp"
        from(components["java"])
    }
}

tasks.register("printVersion") {
    doFirst {
        println(version)
    }
}
