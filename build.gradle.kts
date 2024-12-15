plugins {
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "8.3.5"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))
tasks.shadowJar { archiveClassifier.set("final"); mergeServiceFiles() }

repositories {
    mavenCentral()
}

dependencies {
    api("org.openrewrite:rewrite-java-21:8.40.3")
    api("org.cadixdev:at:0.1.0-rc1")
    implementation("org.apache.logging.log4j:log4j-core:3.0.0-beta3")
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
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
        val url = if (project.version.toString().endsWith("-SNAPSHOT")) {
            "https://repo.papermc.io/repository/maven-snapshots/"
        } else {
            "https://repo.papermc.io/repository/maven-releases/"
        }
        maven(url) {
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
