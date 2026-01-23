plugins {
    id("org.springframework.boot") version "4.0.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

val grpcVersion = "1.75.0"
val protobufVersion = "4.29.3"

allprojects {
    group = "io.adserve"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    extra["grpcVersion"] = grpcVersion
    extra["protobufVersion"] = protobufVersion
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    // Enable preview features for compilation
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("--enable-preview"))
    }

    // Enable preview features for test execution
    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("--enable-preview")
    }

    // Enable preview features for JavaExec tasks (including bootRun)
    tasks.withType<JavaExec> {
        jvmArgs("--enable-preview")
    }

    configurations {
        named("compileOnly") {
            extendsFrom(configurations.named("annotationProcessor").get())
        }
    }

    dependencies {
        // Spring Boot Starter (common to all services)
        "implementation"("org.springframework.boot:spring-boot-starter")

        // Lombok
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")

        // Testing
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}
