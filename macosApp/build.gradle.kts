plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.siftalpha.macos.SiftAlphaMacAppKt")
}
