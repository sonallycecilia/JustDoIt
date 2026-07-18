plugins {
    `java-library`
    `java-test-fixtures`
    id("io.spring.dependency-management")
}

group = "com.justdoit"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}

dependencies {
    // Expostas aos serviços consumidores (api): validação JWT, filtro de
    // segurança e o handler de exceções vivem aqui e são reusados por todos.
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-security")
    api("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    // Test fixtures: helper de autenticação MockMvc reusado pelos testes dos
    // serviços (spring-security-test é exposto aos consumidores das fixtures).
    testFixturesApi("org.springframework.security:spring-security-test")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
