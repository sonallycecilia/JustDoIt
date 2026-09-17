plugins {
    java
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// Cobertura de testes (JaCoCo) em todos os módulos. O relatório é gerado
// automaticamente ao final de `./gradlew test` — não precisa de tarefa extra.
// Saída por módulo: build/reports/jacoco/test/html/index.html
subprojects {
    apply(plugin = "jacoco")

    tasks.withType<Test>().configureEach {
        finalizedBy(tasks.withType<JacocoReport>())

        // Imprime cada teste individualmente com seu resultado. Sem isto o Gradle
        // só informa que a tarefa passou — e a saída não serve como evidência.
        // O stdout do teste também é impresso: é por ele que sai o valor medido
        // pelos *MetricsTest (linha "[MÉTRICA ...]").
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = true
            displayGranularity = 2
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(test: TestDescriptor) {}
            override fun afterTest(test: TestDescriptor, result: TestResult) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null) {
                    println(
                        "\nRESUMO: ${result.testCount} testes | " +
                        "${result.successfulTestCount} passaram | " +
                        "${result.failedTestCount} falharam | " +
                        "${result.skippedTestCount} ignorados"
                    )
                }
            }
        })
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(true)
        }
    }
}
