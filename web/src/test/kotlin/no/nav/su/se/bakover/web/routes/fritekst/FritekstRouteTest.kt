package no.nav.su.se.bakover.web.routes.fritekst

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.fritekst.Fritekst
import no.nav.su.se.bakover.domain.fritekst.FritekstDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstFeil
import no.nav.su.se.bakover.domain.fritekst.FritekstHentDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.inputvalidation.InputFelter
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringFeilResponse
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringsfeil
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.util.UUID

internal class FritekstRouteTest {

    @Test
    fun `kan hente fritekst`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val fritekst = Fritekst(referanseId = referanseId, FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING, fritekst = "fritekst for vedtak")
        val request = FritekstRequestHent(referanseId = referanseId.toString(), sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name)
        val fritekstMockService = mock<FritekstService> {
            on {
                hentFritekst(
                    FritekstHentDomain(
                        referanseId = referanseId,
                        sakId = sakId,
                        type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING,
                    ),
                )
            } doReturn fritekst.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
            }
            defaultRequest(
                HttpMethod.Post,
                FRITEKST_PATH,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.OK
                deserialize<Fritekst>(bodyAsText()) shouldBe fritekst
            }
        }
    }

    @Test
    fun `Får fritekst feil for ikke eksisterende fritekst`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val request = FritekstRequestHent(referanseId = referanseId.toString(), sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name)
        val fritekstMockService = mock<FritekstService> {
            on {
                hentFritekst(
                    FritekstHentDomain(
                        referanseId = referanseId,
                        sakId = sakId,
                        type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING,
                    ),
                )
            } doReturn FritekstFeil.FantIkkeFritekst.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
            }

            defaultRequest(
                HttpMethod.Post,
                FRITEKST_PATH,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldBe FeilResponser.fantIkkeFritekst.json
            }
        }
    }

    @Test
    fun `Kan lagre fritekst`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val friteksttekst = "fritekst for vedtak"
        val fritekstDomain = FritekstDomain(referanseId = referanseId, type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING, fritekst = friteksttekst, sakId = sakId)
        val fritekstMockService = mock<FritekstService>()

        doNothing()
            .`when`(fritekstMockService)
            .lagreFritekst(fritekstDomain)

        val request = FritekstRequestLagre(referanseId = referanseId.toString(), sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name, fritekst = friteksttekst)
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
            }
            defaultRequest(
                HttpMethod.Post,
                "$FRITEKST_PATH/lagre",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
        verify(fritekstMockService).lagreFritekst(fritekstDomain)
    }

    @Test
    fun `Sjekker at lagring kun skjer hvis det blir riktig domeneobjekt`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val friteksttekst = "fritekst for vedtak"

        val request = FritekstRequestLagre(referanseId = "", sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name, fritekst = friteksttekst)
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
            }
            defaultRequest(
                HttpMethod.Post,
                "$FRITEKST_PATH/lagre",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldBe FeilResponser.ugyldigBody("Ugyldig verdi for felt 'referanseId': ''").json
            }
        }
    }

    private fun ugyldigInput(vararg feil: UgyldigInputValideringsfeil) = UgyldigInputValideringFeilResponse(
        message = "Ugyldig input",
        code = "ugyldig_felt_input",
        errors = feil.toList(),
    )

    private fun lagreRequest(fritekst: String) = FritekstRequestLagre(
        referanseId = UUID.randomUUID().toString(),
        sakId = UUID.randomUUID().toString(),
        type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name,
        fritekst = fritekst,
    )

    @Test
    fun `avviser mistenkelig innhold i strengfelt uten å kalle FritekstService`() {
        val mistenkelig = "javascript:alert(1)"
        listOf(
            Triple("fritekst", "$FRITEKST_PATH/lagre", serialize(lagreRequest(fritekst = mistenkelig))),
            Triple(
                "type",
                FRITEKST_PATH,
                serialize(FritekstRequestHent(referanseId = UUID.randomUUID().toString(), sakId = UUID.randomUUID().toString(), type = mistenkelig)),
            ),
        ).forEach { (felt, uri, body) ->
            val fritekstMockService = mock<FritekstService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
                }
                defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)) {
                    setBody(body)
                }.apply {
                    withClue("$uri - $felt") {
                        status shouldBe HttpStatusCode.BadRequest
                        deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe
                            ugyldigInput(UgyldigInputValideringsfeil(felt, "inneholder mistenkelig innhold"))
                    }
                }
            }
            verifyNoInteractions(fritekstMockService)
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, "$FRITEKST_PATH/lagre", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(lagreRequest(fritekst = "<b>hei</b>")))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("fritekst", "inneholder tegn utenfor tillatt tegnsett: '<>'"),
                )
            }
        }
    }

    @Test
    fun `avviser fritekst over maks fritekstlengde`() {
        val maks = InputFelter.FRITEKST.maksLengde
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, "$FRITEKST_PATH/lagre", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(lagreRequest(fritekst = "a".repeat(maks + 1))))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("fritekst", "for lang verdi, antall tegn ${maks + 1}, maks $maks"),
                )
            }
        }
    }

    @Test
    fun `lagrer fritekst opp til maks fritekstlengde`() {
        val fritekstMockService = mock<FritekstService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
            }
            defaultRequest(HttpMethod.Post, "$FRITEKST_PATH/lagre", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(lagreRequest(fritekst = "a".repeat(InputFelter.FRITEKST.maksLengde))))
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
        verify(fritekstMockService).lagreFritekst(any())
    }

    @Test
    fun `svarer ugyldig body uten å kalle FritekstService når bodyen ikke er json eller ikke passer DTO-en`() {
        listOf("{ikke json", "{}").forEach { body ->
            val fritekstMockService = mock<FritekstService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
                }
                defaultRequest(HttpMethod.Post, "$FRITEKST_PATH/lagre", listOf(Brukerrolle.Saksbehandler)) {
                    setBody(body)
                }.apply {
                    withClue(body) {
                        status shouldBe HttpStatusCode.BadRequest
                        bodyAsText() shouldContain "ugyldig_body"
                    }
                }
            }
            verifyNoInteractions(fritekstMockService)
        }
    }
}
