package no.nav.su.se.bakover.web.routes.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.contentType
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class BekreftAvvistKlageTest {

    private val sakId: UUID = UUID.randomUUID()
    private val klageId: UUID = UUID.randomUUID()
    private val uri = "$sakPath/$sakId/klager/$klageId/avvist/bekreft"

    @Test
    fun `ingen tilgang gir unauthorized`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                uri,
                emptyList(),
            ).apply {
                response.status() shouldBe HttpStatusCode.Unauthorized
                response.content shouldBe null
            }
        }
    }

    @Test
    fun `Kun saksbehandler skal ha tilgang`() {
        listOf(
            listOf(Brukerrolle.Veileder),
            listOf(Brukerrolle.Attestant),
            listOf(Brukerrolle.Drift),
        ).forEach {
            withTestApplication(
                {
                    testSusebakover()
                },
            ) {
                defaultRequest(
                    HttpMethod.Post,
                    uri,
                    it,
                ).apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                    response.content shouldBe "{\"message\":\"Bruker mangler en av de tillatte rollene: Saksbehandler.\"}"
                }
            }
        }
    }

    @Test
    fun `fant ikke klage`() {
        verifiserFeilkode(
            feilkode = KunneIkkeBekrefteKlagesteg.FantIkkeKlage,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke klage\",\"code\":\"fant_ikke_klage\"}",
        )
    }

    @Test
    fun `ugyldig tilstand`() {
        verifiserFeilkode(
            feilkode = KunneIkkeBekrefteKlagesteg.UgyldigTilstand(OpprettetKlage::class, AvvistKlage.Bekreftet::class),
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Kan ikke gå fra tilstanden OpprettetKlage til tilstanden Bekreftet\",\"code\":\"ugyldig_tilstand\"}",
        )
    }

    private fun verifiserFeilkode(
        feilkode: KunneIkkeBekrefteKlagesteg,
        status: HttpStatusCode,
        body: String,
    ) {
        val klageServiceMock = mock<KlageService> {
            on { bekreftAvvistFritekst(any(), any()) } doReturn feilkode.left()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            },
        ) {
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler))
        }.apply {
            response.status() shouldBe status
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            response.content shouldBe body
        }
    }
}
