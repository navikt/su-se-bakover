package no.nav.su.se.bakover.web.routes.kontrollsamtale

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.kontrollsamtale.domain.kontrollnotat.KontrollsamtaleNotatService
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.inputvalidation.InputFelter
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringFeilResponse
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringsfeil
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
import java.time.LocalDate
import java.util.UUID

internal class KontrollsamtaleNotatRouteTest {
    private val sakId = UUID.randomUUID()
    private val notatId = UUID.randomUUID()
    private val url = "/saker/$sakId/kontrollsamtaler/notat"
    private val dto = KontrollNotatDto(
        id = notatId.toString(),
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
        val ugyldigDto = dto.copy(id = "ikke-en-uuid")
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

    private val mistenkelig = "javascript:alert(1)"

    private fun ugyldigInput(vararg feil: UgyldigInputValideringsfeil) = UgyldigInputValideringFeilResponse(
        message = "Ugyldig input",
        code = "ugyldig_felt_input",
        errors = feil.toList(),
    )

    private fun postNotat(
        body: String,
        service: KontrollsamtaleNotatService,
        assertions: suspend HttpResponse.() -> Unit,
    ) {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(kontrollsamtaleNotatService = service),
                )
            }
            defaultRequest(HttpMethod.Post, url, listOf(Brukerrolle.Veileder)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.apply { assertions() }
        }
    }

    @Test
    fun `avviser mistenkelig innhold i alle strengfelt uten servicekall`() {
        val reise = ReiseDatoBody(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1))
        listOf(
            "id" to serialize(dto.copy(id = mistenkelig)),
            "fritekst" to serialize(dto.copy(fritekst = mistenkelig)),
            "utenlandsoppholdDatoer[0].utreiseDato" to serialize(dto.copy(utenlandsoppholdDatoer = listOf(reise)))
                .replace("\"2026-01-01\"", "\"$mistenkelig\""),
        ).forEach { (felt, body) ->
            val service = mock<KontrollsamtaleNotatService>()
            postNotat(body, service) {
                withClue(felt) {
                    status shouldBe HttpStatusCode.BadRequest
                    deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe
                        ugyldigInput(UgyldigInputValideringsfeil(felt, "inneholder mistenkelig innhold"))
                }
            }
            verifyNoInteractions(service)
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        val service = mock<KontrollsamtaleNotatService>()
        postNotat(serialize(dto.copy(fritekst = "<b>hei</b>")), service) {
            status shouldBe HttpStatusCode.BadRequest
            deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                UgyldigInputValideringsfeil("fritekst", "inneholder tegn utenfor tillatt tegnsett: '<>'"),
            )
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `godtar fritekst opp til maks fritekstlengde`() {
        val service = mock<KontrollsamtaleNotatService> {
            on { lagre(any(), any()) } doReturn notat.right()
        }
        postNotat(serialize(dto.copy(fritekst = "a".repeat(InputFelter.FRITEKST.maksLengde))), service) {
            status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `avviser fritekst over maks fritekstlengde`() {
        val service = mock<KontrollsamtaleNotatService>()
        val lengde = InputFelter.FRITEKST.maksLengde + 1
        postNotat(serialize(dto.copy(fritekst = "a".repeat(lengde))), service) {
            status shouldBe HttpStatusCode.BadRequest
            deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                UgyldigInputValideringsfeil(
                    "fritekst",
                    "for lang verdi, antall tegn $lengde, maks ${InputFelter.FRITEKST.maksLengde}",
                ),
            )
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `svarer ugyldig body uten servicekall når bodyen ikke er json eller ikke passer DTO-en`() {
        listOf("{ikke json", "{}").forEach { body ->
            val service = mock<KontrollsamtaleNotatService>()
            postNotat(body, service) {
                withClue(body) {
                    status shouldBe HttpStatusCode.BadRequest
                    bodyAsText() shouldContain "ugyldig_body"
                }
            }
            verifyNoInteractions(service)
        }
    }
}
