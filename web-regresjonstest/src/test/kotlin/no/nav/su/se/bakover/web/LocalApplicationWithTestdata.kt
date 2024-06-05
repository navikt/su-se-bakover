package no.nav.su.se.bakover.web

import ch.qos.logback.classic.ClassicConstants
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.test.tilbakekreving.tilbakekrevingskomponenterMedClientStubs
import org.slf4j.bridge.SLF4JBridgeHandler

fun main() {
    if (!ApplicationConfig.isRunningLocally()) {
        throw IllegalStateException("You should not run this main method on nais (preprod/prod. See Application.main() instead")
    }
    System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback-local.xml")
    // https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = {
            susebakover(
                tilbakekrevingskomponenter = { clock, sessionFactory, _, hendelsekonsumenterRepo, sak, oppgave, oppgaveHendelseRepo, mapRåttKravgrunnlagPåSakHendelse, hendelseRepo, dokumentHendelseRepo, brevService, _, tilgangstyringService ->
                    tilbakekrevingskomponenterMedClientStubs(
                        clock = clock,
                        sessionFactory = sessionFactory,
                        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                        sakService = sak,
                        oppgaveService = oppgave,
                        oppgaveHendelseRepo = oppgaveHendelseRepo,
                        mapRåttKravgrunnlagPåSakHendelse = mapRåttKravgrunnlagPåSakHendelse,
                        hendelseRepo = hendelseRepo,
                        dokumentHendelseRepo = dokumentHendelseRepo,
                        brevService = brevService,
                        tilgangstyringService = tilgangstyringService,
                    )
                },
                disableConsumersAndJobs = false,
            ) {
                this.testDataRoutes()
            }
        },
    ).start(true)
}
