import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // La scheda produce la definizione da combattimento usata dal motore.
    api(project(":engine:domain-model"))
    api(project(":engine:character-rules"))
    implementation(project(":engine:rules-model"))
    api(libs.kotlinx.serialization.json)
    implementation(project(":engine:persistence-json"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
