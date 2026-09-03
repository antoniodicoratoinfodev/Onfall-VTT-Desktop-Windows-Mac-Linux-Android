plugins {
    `java-library`
}

dependencies {
    api(project(":engine:rules-model"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(libs.versions.jvmTarget.get().toInt())
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
