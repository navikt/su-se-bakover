package no.nav.su.se.bakover.service.brev

import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.DockerImageName
import java.time.Duration

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
            // Bruker versjon i tag publisert 6 november 2026:
            // https://github.com/navikt/pensjonsbrev/pkgs/container/pensjonsbrev%2Fpdf-bygger/1518344620?tag=main
            // Main er bakoveerkompatibel
            DockerImageName.parse("ghcr.io/navikt/pensjonsbrev/pdf-bygger:main"),
        )
            .withImagePullPolicy(PullPolicy.alwaysPull())
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
