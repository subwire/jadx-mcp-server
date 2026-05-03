plugins {
    application
    id("java")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("io.github.skylot:jadx-core:1.5.5")
    implementation("io.modelcontextprotocol.sdk:mcp:0.17.1")
    implementation("org.slf4j:slf4j-simple:2.0.12")
    runtimeOnly("io.github.skylot:jadx-dex-input:1.5.5")
}

application {
    mainClass.set("jadxmcp.Main")
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(17)
}
