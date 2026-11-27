package no.nav.su.se.bakover.service.brev

import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * Felles lås for testklasser som bruker pdf-bygger-containeren. Brukes med `@ResourceLock(PDF_BYGGER_RESSURS)`,
 * slik at bare én klasse sender bestillinger om gangen. På maskiner med arm64 kjører amd64-bildet emulert,
 * og mange samtidige LaTeX-kompileringer gir tidsavbrudd.
 */
const val PDF_BYGGER_RESSURS = "pdf-bygger"

/**
 * Starter én pdf-bygger-container per JVM og deler den mellom alle testklasser som bruker extensionen.
 * Containeren stoppes når rot-konteksten lukkes.
 */
class PdfByggerExtension : ParameterResolver {
    companion object {
        private const val KEY = "GLOBAL_PDF_BYGGER"
    }

    private class PdfByggerContainer : ExtensionContext.Store.CloseableResource {
        private val container = GenericContainer(
            // Låst til digest, slik at samme kode alltid testes mot samme pdf-bygger.
            // Dette er bildet som var tag main 26. november 2026 (bygget 12:05 UTC).
            // Oppdater digesten bevisst når en nyere pdf-bygger skal tas i bruk:
            // docker buildx imagetools inspect ghcr.io/navikt/pensjonsbrev/pdf-bygger:main
            DockerImageName.parse(
                "ghcr.io/navikt/pensjonsbrev/pdf-bygger@sha256:213886ded10f4824d9b79fe103d397a2e2afd7be783208f3a63cd4e97946a5d2",
            ),
        )
            .withExposedPorts(8080)
            .withCreateContainerCmdModifier { it.withPlatform("linux/amd64") }
            .withEnv("JAVA_TOOL_OPTIONS", "-Dio.ktor.development=true")
            .withEnv("PDF_BYGGER_COMPILE_TMP_DIR", "/tmp")
            .withEnv("PDF_COMPILE_TIMEOUT_SECONDS", "200")
            .waitingFor(Wait.forHttp("/isReady").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)))
            .also { it.start() }

        val klient = PdfByggerKlientImpl(
            "http://${container.host}:${container.getMappedPort(8080)}/",
            AzureClientStub,
            "",
        )

        override fun close() {
            container.stop()
        }
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == PdfByggerKlientImpl::class.java
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): PdfByggerKlientImpl {
        val store = extensionContext.root.getStore(ExtensionContext.Namespace.GLOBAL)
        return (store.getOrComputeIfAbsent(KEY) { PdfByggerContainer() } as PdfByggerContainer).klient
    }
}
