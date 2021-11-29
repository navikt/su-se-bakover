package no.nav.su.se.bakover.web.routes.kontrollsamtale

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.kontrollsamtale.KunneIkkeKalleInnTilKontrollsamtale
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class KontrollsamtaleRoutesKtTest {

    private val validBody = """
        {"sakId": "${UUID.randomUUID()}"}
    """.trimIndent()

    @Test
    fun `må være innlogget for å kalle inn til kontrollsamtale`() {
        withTestApplication(
            { testSusebakover() },
        ) {
            handleRequest(HttpMethod.Post, "/kontrollsamtale/kallInn")
        }.apply {
            response.status() shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `fant ikke sak`() {
        verifiserFeilkode(
            feilkode = KunneIkkeKalleInnTilKontrollsamtale.FantIkkeSak,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke sak\",\"code\":\"fant_ikke_sak\"}",
        )
    }

    @Test
    fun `fant ikke person`() {
        verifiserFeilkode(
            feilkode = KunneIkkeKalleInnTilKontrollsamtale.FantIkkePerson,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke person\",\"code\":\"fant_ikke_person\"}",
        )
    }

    @Test
    fun `kunne ikke generere dokument`() {
        verifiserFeilkode(
            feilkode = KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeGenerereDokument,
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Feil ved generering av dokument\",\"code\":\"feil_ved_generering_av_dokument\"}",
        )
    }

    @Test
    fun `kunne ikke hente navn for saksbehandler eller attestant`() {
        verifiserFeilkode(
            feilkode = KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke saksbehandler eller attestant\",\"code\":\"fant_ikke_saksbehandler_eller_attestant\"}",
        )
    }

    @Test
    fun `kunne ikke kalle inn`() {
        verifiserFeilkode(
            feilkode = KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeKalleInn,
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Kunne ikke kalle inn til kontrollsamtale\",\"code\":\"kunne_ikke_kalle_inn_til_kontrollsamtale\"}",
        )
    }

    private fun verifiserFeilkode(
        feilkode: KunneIkkeKalleInnTilKontrollsamtale,
        status: HttpStatusCode,
        body: String,
    ) {
        val kontrollsamtaleServiceMock = mock<KontrollsamtaleService> {
            on { kallInn(any(), any()) } doReturn feilkode.left()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(kontrollsamtale = kontrollsamtaleServiceMock),
                )
            },
        ) {
            defaultRequest(HttpMethod.Post, "/kontrollsamtale/kallInn", listOf(Brukerrolle.Saksbehandler)) {
                setBody(validBody)
            }
        }.apply {
            response.status() shouldBe status
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            response.content shouldBe body
        }
    }

    @Test
    fun `saksbehandler kan kalle inn til kontrollsamtale`() {
        val kontrollsamtaleServiceMock = mock<KontrollsamtaleService> {
            on { kallInn(any(), any()) } doReturn Unit.right()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(kontrollsamtale = kontrollsamtaleServiceMock),
                )
            },
        ) {
            defaultRequest(HttpMethod.Post, "/kontrollsamtale/kallInn", listOf(Brukerrolle.Saksbehandler)) {
                setBody(validBody)
            }
        }.apply {

            response.status() shouldBe HttpStatusCode.OK
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            response.content shouldBe "{\"status\": \"OK\"}"
        }
    }
}
