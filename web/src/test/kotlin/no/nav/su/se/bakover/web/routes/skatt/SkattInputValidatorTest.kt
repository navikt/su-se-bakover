package no.nav.su.se.bakover.web.routes.skatt

import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringFeilResponse
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringsfeil
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import person.domain.PersonService
import vilkår.skatt.application.SkatteService

/**
 * Verifiserer at strengverdiene i JSON-bodyen til skatte-endepunktene saniteres før de når [SkatteService].
 * Hvert case gjør nøyaktig ett felt ugyldig, slik at et endepunkt som ikke validerer bodyen, gir rød test.
 */
internal class SkattInputValidatorTest {

    private val stier = listOf("$SKATTE_PATH/forhandsvis", SKATTE_PATH, "$SKATTE_PATH/uten-verifisering")

    private val mistenkelig = "javascript:alert(1)"
    private val forventetMelding = "inneholder mistenkelig innhold"

    private val gyldigBody = FrioppslagRequestBody(
        fnr = fnr.toString(),
        epsFnr = null,
        år = 2020,
        begrunnelse = "Begrunnelse for oppslag",
        sakstype = Sakstype.ALDER.value,
        fagsystemId = "",
    )

    private fun ugyldigInput(vararg feil: UgyldigInputValideringsfeil) = UgyldigInputValideringFeilResponse(
        message = "Ugyldig input",
        code = "ugyldig_felt_input",
        errors = feil.toList(),
    )

    private val ugyldigeFelt = listOf(
        "fnr" to gyldigBody.copy(fnr = mistenkelig),
        "epsFnr" to gyldigBody.copy(epsFnr = mistenkelig),
        "begrunnelse" to gyldigBody.copy(begrunnelse = mistenkelig),
        "sakstype" to gyldigBody.copy(sakstype = mistenkelig),
        "fagsystemId" to gyldigBody.copy(fagsystemId = mistenkelig),
    )

    @Test
    fun `avviser mistenkelig innhold i alle strengfelt uten å kalle SkatteService`() {
        stier.forEach { sti ->
            ugyldigeFelt.forEach { (felt, body) ->
                val skatteService = mock<SkatteService>()
                testApplication {
                    application {
                        testSusebakoverWithMockedDb(services = TestServicesBuilder.services(skatteService = skatteService))
                    }
                    defaultRequest(HttpMethod.Post, sti, listOf(Brukerrolle.Saksbehandler)) {
                        setBody(serialize(body))
                    }.apply {
                        withClue("$sti - $felt") {
                            status shouldBe HttpStatusCode.BadRequest
                            deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe
                                ugyldigInput(UgyldigInputValideringsfeil(felt, forventetMelding))
                        }
                    }
                }
                verifyNoInteractions(skatteService)
            }
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        val skatteService = mock<SkatteService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(skatteService = skatteService))
            }
            defaultRequest(HttpMethod.Post, "$SKATTE_PATH/forhandsvis", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(gyldigBody.copy(begrunnelse = "<script>alert(1)</script>")))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("begrunnelse", "inneholder tegn utenfor tillatt tegnsett: '<>'"),
                )
            }
        }
        verifyNoInteractions(skatteService)
    }

    @Test
    fun `godtar begrunnelse på maks lengde`() {
        val begrunnelse = "a".repeat(BEGRUNNELSE_MAKS_LENGDE)
        val pdf = PdfA("skatte-pdf".toByteArray())
        val skatteService = mock<SkatteService> {
            on { hentOgLagSkattePdf(any()) } doReturn pdf.right()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(skatteService = skatteService, person = personService),
                )
            }
            defaultRequest(HttpMethod.Post, "$SKATTE_PATH/forhandsvis", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(gyldigBody.copy(begrunnelse = begrunnelse)))
            }.apply {
                status shouldBe HttpStatusCode.OK
                readRawBytes() shouldBe pdf.getContent()
                contentType() shouldBe ContentType.Application.Pdf
            }
        }
        verify(skatteService).hentOgLagSkattePdf(argThat { it.begrunnelse shouldBe begrunnelse })
    }

    @Test
    fun `avviser begrunnelse over maks lengde`() {
        val begrunnelse = "a".repeat(BEGRUNNELSE_MAKS_LENGDE + 1)
        stier.forEach { sti ->
            val skatteService = mock<SkatteService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = TestServicesBuilder.services(skatteService = skatteService))
                }
                defaultRequest(HttpMethod.Post, sti, listOf(Brukerrolle.Saksbehandler)) {
                    setBody(serialize(gyldigBody.copy(begrunnelse = begrunnelse)))
                }.apply {
                    withClue(sti) {
                        status shouldBe HttpStatusCode.BadRequest
                        deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                            UgyldigInputValideringsfeil(
                                "begrunnelse",
                                "for lang verdi, antall tegn ${begrunnelse.length}, maks $BEGRUNNELSE_MAKS_LENGDE",
                            ),
                        )
                    }
                }
            }
            verifyNoInteractions(skatteService)
        }
    }

    @Test
    fun `svarer ugyldig body uten å kalle SkatteService når bodyen ikke er json eller ikke passer DTO-en`() {
        val ikkeJson = "{ikke json"
        val manglerPåkrevdFelt = "{}"
        stier.forEach { sti ->
            listOf(ikkeJson, manglerPåkrevdFelt).forEach { body ->
                val skatteService = mock<SkatteService>()
                testApplication {
                    application {
                        testSusebakoverWithMockedDb(services = TestServicesBuilder.services(skatteService = skatteService))
                    }
                    defaultRequest(HttpMethod.Post, sti, listOf(Brukerrolle.Saksbehandler)) {
                        setBody(body)
                    }.apply {
                        withClue("$sti - $body") {
                            status shouldBe HttpStatusCode.BadRequest
                            bodyAsText() shouldContain "ugyldig_body"
                        }
                    }
                }
                verifyNoInteractions(skatteService)
            }
        }
    }
}
