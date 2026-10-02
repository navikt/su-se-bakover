package no.nav.su.se.bakover.web.routes.kontrollsamtale

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.kontrollsamtale.domain.kontrollnotat.KontrollsamtaleNotatService
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.util.UUID

internal class KontrollsamtaleNotatRouteTest {
    private val sakId = UUID.randomUUID()
    private val notatId = UUID.randomUUID()
    private val url = "/saker/$sakId/kontrollsamtaler/notat"
    private val dto = KontrollNotatDto(
        kontrollnotatId = notatId.toString(),
        personligOppmøte = true,
        fullmaktOgLegeerklæring = null,
        originalPass = true,
        gyldigPass = true,
        harVærtUtenlands = false,
        utenlandsoppholdDatoer = emptyList(),
        harPlanerOmUtenlandsreise = false,
        planlagteUtenlandsreiseDatoer = emptyList(),
        reiseDokumentasjon = false,
        økonomiskSituasjon = false,
        andreForhold = false,
        skatteOpplysninger = false,
        fritekst = null,
    )
    private val notat = dto.toDomain(notatId, sakId, fixedTidspunkt)

    @Test
    fun `begge POST-kall bruker samme kontrollnotat-ID fra frontend`() {
        val service = mock<KontrollsamtaleNotatService> {
            on { lagre(any(), any()) } doReturn notat.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(kontrollsamtaleNotatService = service),
                )
            }
            repeat(2) {
                defaultRequest(HttpMethod.Post, url, listOf(Brukerrolle.Veileder)) {
                    contentType(ContentType.Application.Json)
                    setBody(serialize(dto))
                }.status shouldBe HttpStatusCode.OK
            }
        }
        val notatCaptor = argumentCaptor<KontrollsamtaleNotat>()
        verify(service, times(2)).lagre(eq(sakId), notatCaptor.capture())
        notatCaptor.allValues shouldBe listOf(notat, notat)
    }

    @Test
    fun `ugyldig kontrollnotat-ID gir 400 uten servicekall`() {
        val service = mock<KontrollsamtaleNotatService>()
        val ugyldigDto = dto.copy(kontrollnotatId = "ikke-en-uuid")
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(kontrollsamtaleNotatService = service),
                )
            }
            defaultRequest(HttpMethod.Post, url, listOf(Brukerrolle.Veileder)) {
                contentType(ContentType.Application.Json)
                setBody(serialize(ugyldigDto))
            }.status shouldBe HttpStatusCode.BadRequest
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `kontrollnotat-ID brukt på annen sak gir 409`() {
        val service = mock<KontrollsamtaleNotatService> {
            on { lagre(any(), any()) } doReturn KontrollsamtaleNotatService.KontrollnotatIdAlleredeBrukt.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(kontrollsamtaleNotatService = service),
                )
            }
            defaultRequest(HttpMethod.Post, url, listOf(Brukerrolle.Veileder)) {
                contentType(ContentType.Application.Json)
                setBody(serialize(dto))
            }.status shouldBe HttpStatusCode.Conflict
        }
    }

    @Test
    fun `feil ved henting av sak eller person gir fortsatt 500`() {
        val feil = KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost(
            sakId = sakId,
            kontrollsamtaleNotatId = notatId,
            grunn = "Kunne ikke hente sak for å opprette journalpost",
        )
        val service = mock<KontrollsamtaleNotatService> {
            on { lagre(any(), any()) } doReturn feil.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(kontrollsamtaleNotatService = service),
                )
            }
            defaultRequest(HttpMethod.Post, url, listOf(Brukerrolle.Veileder)) {
                contentType(ContentType.Application.Json)
                setBody(serialize(dto))
            }.status shouldBe HttpStatusCode.InternalServerError
        }
    }
}
