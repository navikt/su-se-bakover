package no.nav.su.se.bakover.web.routes.historisk

import arrow.core.left
import arrow.core.right
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingStatus
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskInfotrygdSimulering
import no.nav.su.se.bakover.service.historisk.revurdering.HistoriskInfotrygdRevurderingService
import no.nav.su.se.bakover.test.historisk.HistoriskInfotrygdOppdragTestdata
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.slf4j.LoggerFactory
import person.domain.KunneIkkeHentePerson
import person.domain.PersonService

@Isolated
internal class HistoriskSimuleringRoutesTest {
    private val data = HistoriskInfotrygdOppdragTestdata
    private val fnr = Fnr("12345678910")
    private val beregnet = data.revurdering()
    private val sakInfo = SakInfo(beregnet.sakId, Saksnummer(2021L), fnr, Sakstype.ALDER)
    private val path = "$HISTORISK_ALDERSSAK_PATH/revurderinger/${beregnet.id.value}/simuler"
    private val auditLogger = "auditLogger"
    private val simulert = beregnet.simuler(
        HistoriskInfotrygdSimulering(
            requireNotNull(beregnet.beregning),
            beregnet.periode,
            data.tidspunkt,
            false,
            data.simuleringsrespons(fnr),
        ),
        data.saksbehandler,
        data.tidspunkt,
    ).fold({ error(it.toString()) }, { it })

    @Test
    fun `saksbehandler kan simulere med persontilgang og audit`() {
        val service = service()
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(fnr, Sakstype.ALDER) } doReturn Unit.right()
        }
        val logger = LoggerFactory.getLogger(auditLogger) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(person = personService, historiskInfotrygdRevurderingService = service),
                    )
                }
                val response = defaultRequest(HttpMethod.Post, path, listOf(Brukerrolle.Saksbehandler))
                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                deserialize<HistoriskInfotrygdRevurderingResponse>(body) shouldBe simulert.toResponse(false)
                body shouldNotContain "råRespons"
                body shouldNotContain requireNotNull(simulert.simulering).respons.råRespons
            }
            val audit = appender.list.single { it.formattedMessage.contains("flexString2=${beregnet.id.value}") }
            audit.formattedMessage shouldContain AuditLogEvent.Action.UPDATE.value
            audit.formattedMessage shouldContain "duid=$fnr"
            verify(personService).sjekkTilgangTilPerson(fnr, Sakstype.ALDER)
            verify(service).simuler(eq(beregnet.id), any())
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `manglende rolle avvises før behandling og person hentes`() {
        val service = service()
        val personService = mock<PersonService>()
        Brukerrolle.entries.filterNot { it == Brukerrolle.Saksbehandler }.forEach { rolle ->
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(person = personService, historiskInfotrygdRevurderingService = service),
                    )
                }
                defaultRequest(HttpMethod.Post, path, listOf(rolle)).status shouldBe
                    HttpStatusCode.Forbidden
            }
        }
        verifyNoInteractions(service, personService)
    }

    @Test
    fun `manglende token gir ikke tilgang til simulering`() {
        val service = service()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(historiskInfotrygdRevurderingService = service),
                )
            }
            client.post(path).status shouldBe HttpStatusCode.Unauthorized
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `manglende persontilgang stopper simulering og auditerer oppslaget`() {
        val service = service()
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(fnr, Sakstype.ALDER) } doReturn KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
        }
        val logger = LoggerFactory.getLogger(auditLogger) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(person = personService, historiskInfotrygdRevurderingService = service),
                    )
                }
                defaultRequest(HttpMethod.Post, path, listOf(Brukerrolle.Saksbehandler)).status shouldBe
                    HttpStatusCode.Forbidden
            }
            appender.list.single { it.formattedMessage.contains("flexString2=${beregnet.id.value}") }
                .formattedMessage shouldContain AuditLogEvent.Action.SEARCH.value
            verify(service, never()).simuler(any(), any())
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `simuleringssperren vises bare når håndhevingen er aktivert`() {
        val manglerSimulering = "MANGLER_SIMULERING"
        beregnet.toResponse(false).sperregrunnerForAttestering.contains(manglerSimulering) shouldBe false
        beregnet.toResponse(true).sperregrunnerForAttestering.contains(manglerSimulering) shouldBe true
        simulert.toResponse(true).sperregrunnerForAttestering.contains(manglerSimulering) shouldBe false
        simulert.status shouldBe HistoriskInfotrygdRevurderingStatus.SIMULERT
    }

    private fun service(): HistoriskInfotrygdRevurderingService = mock {
        on { hentMedSakInfo(beregnet.id) } doReturn (sakInfo to beregnet)
        on { simuler(eq(beregnet.id), any()) } doReturn simulert.right()
    }
}
