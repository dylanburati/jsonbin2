plugins {
    kotlin("jvm") version "1.3.72"
    application
    id("com.github.johnrengelman.shadow") version "6.0.0"
}

group = "com.dylanburati"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.javalin:javalin:3.9.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.3")
    implementation("org.slf4j:slf4j-simple:1.8.0-beta4")
    runtimeOnly("org.postgresql:postgresql:42.2.14")
    implementation("me.liuwj.ktorm:ktorm-core:3.1.0")
    implementation("me.liuwj.ktorm:ktorm-support-postgresql:3.1.0")
    implementation("me.liuwj.ktorm:ktorm-jackson:3.1.0")
    implementation("com.zaxxer:HikariCP:3.3.1")
    implementation("org.flywaydb:flyway-core:6.5.3")
    implementation("at.favre.lib:bcrypt:0.9.0")
    implementation("io.github.cdimascio:java-dotenv:5.2.1")
    implementation("com.auth0:java-jwt:3.10.3")
    testImplementation(platform("org.junit:junit-bom:5.6.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    test {
        useJUnitPlatform()
    }
    shadowJar {
        archiveClassifier.set("")
        archiveVersion.set("")
    }
}

application {
    mainClassName = "com.dylanburati.jsonbin2.MainKt"
}
