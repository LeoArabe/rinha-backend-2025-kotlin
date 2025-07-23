import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "3.2.2"
	id("io.spring.dependency-management") version "1.1.4"
	id("org.graalvm.buildtools.native") version "0.9.28"
	kotlin("jvm") version "1.9.22"
	kotlin("plugin.spring") version "1.9.22"
}

group = "com.estagiario.gobots"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot Reactive Stack
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
	implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
	implementation("org.springframework.boot:spring-boot-starter-actuator")

	// Kotlin Core
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib")

	// Coroutines
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

	// JSON Processing
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

	// Test Dependencies
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:mongodb")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs += "-Xjsr305=strict"
		jvmTarget = "21"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// GraalVM Native Configuration
graalvmNative {
	binaries {
		named("main") {
			imageName.set("rinha-backend-app")
			mainClass.set("com.estagiario.gobots.rinha_backend.RinhaBackendApplicationKt")

			// Memory optimization for container limits
			buildArgs.add("--initialize-at-build-time=org.slf4j")
			buildArgs.add("--initialize-at-build-time=ch.qos.logback")
			buildArgs.add("-H:+ReportExceptionStackTraces")
			buildArgs.add("-H:+AddAllCharsets")
			buildArgs.add("-H:IncludeResources=.*\\.properties")
			buildArgs.add("-H:IncludeResources=.*\\.yml")

			// Performance optimizations
            buildArgs.add("--gc=Serial")
			buildArgs.add("-H:+UnlockExperimentalVMOptions")
			buildArgs.add("-H:+UseContainerSupport")
		}
	}
}

// Spring Boot configuration for native builds
springBoot {
	buildInfo()
}