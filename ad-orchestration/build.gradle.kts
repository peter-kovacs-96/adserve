import com.google.protobuf.gradle.*

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.protobuf")
}

val grpcVersion = rootProject.extra["grpcVersion"] as String
val protobufVersion = rootProject.extra["protobufVersion"] as String

dependencies {
    // 1. Web & Utility (Modular starters in SB 4)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 2. Resilience4j - commented out, using Spring 7 native resilience
    //implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    // 3. Spring Cloud Circuit Breaker - commented out, using Spring 7 native resilience
    //implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-spring-retry")

    // 4. Manual gRPC (Legacy Style)
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")

    // 5. Observability (Trace IDs for Virtual Threads)
    implementation("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("io.micrometer:micrometer-tracing-bridge-otel")

    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
    }
}

sourceSets {
    main {
        // CRITICAL: Tells the plugin WHERE your .proto files are
        proto {
            srcDir("${rootProject.projectDir}/proto")
        }
        java {
            // Tells the IDE WHERE the generated code is
            srcDirs(
                "build/generated/source/proto/main/java",
                "build/generated/source/proto/main/grpc"
            )
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}