import com.google.protobuf.gradle.*

plugins {
    id("com.google.protobuf")
}

val grpcVersion: String by rootProject.extra
val protobufVersion: String by rootProject.extra

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // gRPC
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")

    // gRPC Spring Boot Starter
    implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")

    // Required for generated code
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

sourceSets {
    main {
        proto {
            srcDir("${rootProject.projectDir}/proto")
            include("user.proto")
        }
        java {
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
