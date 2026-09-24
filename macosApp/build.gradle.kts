plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.siftalpha.macos.SiftAlphaMacAppKt")
}
