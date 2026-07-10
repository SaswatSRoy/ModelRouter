allprojects {
    group = "io.modelrouter"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
