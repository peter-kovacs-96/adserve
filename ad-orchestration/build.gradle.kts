import com.google.protobuf.gradle.*

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.protobuf")
}

val grpcVersion = rootProject.extra["grpcVersion"] as String
val protobufVersion = rootProject.extra["protobufVersion"] as String

dependencies {
    // Web & Utility
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Manual gRPC (Legacy Style)
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")

    // Observability (Trace IDs for Virtual Threads)
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