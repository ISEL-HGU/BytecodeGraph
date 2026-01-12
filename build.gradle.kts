
plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // JDK 21 권장
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.ibm.wala:com.ibm.wala.core:1.6.12")
    implementation("com.ibm.wala:com.ibm.wala.shrike:1.6.12")
    implementation("com.ibm.wala:com.ibm.wala.util:1.6.12")
    implementation("org.apache.bcel:bcel:6.11.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

application {
    // Main 클래스 경로를 프로젝트에 맞게 조정
    mainClass.set("org.example.Main")
}

tasks.withType<JavaExec> {
    standardInput = System.`in`
}