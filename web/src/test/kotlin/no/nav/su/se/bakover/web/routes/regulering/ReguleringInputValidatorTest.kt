package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.left
import common.presentation.grunnlag.UføregrunnlagJson
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
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.domain.regulering.ReguleringGrunnbeløpAutomatiskService
import no.nav.su.se.bakover.domain.regulering.ReguleringManuellService
import no.nav.su.se.bakover.domain.regulering.ReguleringStatusUteståendeService
import no.nav.su.se.bakover.domain.regulering.Reguleringsvariant
import no.nav.su.se.bakover.service.regulering.aldersfradrag.OmregningAldersFradragAutomatiskService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.inputvalidation.InputFelter
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringFeilResponse
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringsfeil
import no.nav.su.se.bakover.web.routes.regulering.omregning.DryRunOmregningBody
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.util.UUID

/**
 * Verifiserer at strengverdiene i JSON-bodyen til regulerings-endepunktene saniteres før de når servicene.
 * Hvert case gjør nøyaktig ett felt ugyldig, slik at et endepunkt som ikke validerer bodyen, gir rød test.
 */
internal class ReguleringInputValidatorTest {

    private val sakId: UUID = UUID.randomUUID()
    private val reguleringId: UUID = UUID.randomUUID()
    private val manuellUri = "$REGULERING_PATH/manuell"
    private val reguleringUri = "$manuellUri/$reguleringId"
    private val automatiskUri = "$REGULERING_PATH/automatisk"

    private val mistenkelig = "javascript:alert(1)"
    private val forventetMelding = "inneholder mistenkelig innhold"
    private val gyldigMåned = "2026-05"
    private val gyldigDato = "2026-05-01"

    private data class UgyldigFeltCase(
        val felt: String,
        val uri: String,
        val rolle: Brukerrolle,
        val body: String,
    )

    private class Mocks {
        val manuell = mock<ReguleringManuellService>()
        val grunnbeløpAutomatisk = mock<ReguleringGrunnbeløpAutomatiskService>()
        val statusUtestående = mock<ReguleringStatusUteståendeService>()
        val omregning = mock<OmregningAldersFradragAutomatiskService>()

        fun services() = TestServicesBuilder.services(
            reguleringManuellService = manuell,
            reguleringGrunnbeløpAutomatiskService = grunnbeløpAutomatisk,
            reguleringStatusUteståendeService = statusUtestående,
        ).copy(omregningAldersFradragAutomatiskService = omregning)

        fun verifiserIngenKall() = verifyNoInteractions(manuell, grunnbeløpAutomatisk, statusUtestående, omregning)
    }

    private fun opprett(
        begrunnelse: String = "begrunnelse",
        fraOgMed: String? = gyldigMåned,
    ) = serialize(
        OpprettReguleringRequest(
            begrunnelse = begrunnelse,
            reguleringsvariant = Reguleringsvariant.GRUNNBELØP,
            fraOgMed = fraOgMed,
        ),
    )

    private fun beregn(
        fradragBeskrivelse: String? = null,
        uføreTilOgMed: String = "2026-12-31",
    ) = serialize(
        BeregnReguleringRequest(
            fradrag = listOf(
                LegacyFradragRequestJson(
                    periode = PeriodeJson(gyldigDato, "2026-12-31"),
                    type = "Arbeidsinntekt",
                    beskrivelse = fradragBeskrivelse,
                    beløp = 1000.0,
                    utenlandskInntekt = null,
                    tilhører = "BRUKER",
                ),
            ),
            uføre = listOf(
                UføregrunnlagJson(
                    id = UUID.randomUUID().toString(),
                    opprettet = "2026-01-01T00:00:00Z",
                    periode = PeriodeJson(gyldigDato, uføreTilOgMed),
                    uføregrad = 100,
                    forventetInntekt = 0,
                ),
            ),
        ),
    )

    private fun ugyldigInput(vararg feil: UgyldigInputValideringsfeil) = UgyldigInputValideringFeilResponse(
        message = "Ugyldig input",
        code = "ugyldig_felt_input",
        errors = feil.toList(),
    )

    private val ugyldigeFelt = listOf(
        UgyldigFeltCase("begrunnelse", "$manuellUri/opprett/$sakId", Brukerrolle.Saksbehandler, opprett(begrunnelse = mistenkelig)),
        UgyldigFeltCase("fraOgMed", "$manuellUri/opprett/$sakId", Brukerrolle.Saksbehandler, opprett(fraOgMed = mistenkelig)),

        UgyldigFeltCase("fradrag[0].beskrivelse", "$reguleringUri/beregn", Brukerrolle.Saksbehandler, beregn(fradragBeskrivelse = mistenkelig)),
        UgyldigFeltCase("uføre[0].periode.tilOgMed", "$reguleringUri/beregn", Brukerrolle.Saksbehandler, beregn(uføreTilOgMed = mistenkelig)),

        UgyldigFeltCase("kommentar", "$reguleringUri/attestering/underkjenn", Brukerrolle.Attestant, serialize(UnderkjennReguleringBody(kommentar = mistenkelig))),

        UgyldigFeltCase("begrunnelse", "$reguleringUri/avslutt", Brukerrolle.Saksbehandler, serialize(AvsluttReguleringRequest(begrunnelse = mistenkelig))),

        UgyldigFeltCase("fraOgMedMåned", automatiskUri, Brukerrolle.Drift, serialize(AutomatiskKjøringBody(fraOgMedMåned = mistenkelig))),
        UgyldigFeltCase(
            "dryRunGrunnbeløp.omregningsfaktor",
            "$automatiskUri/dry",
            Brukerrolle.Drift,
            serialize(
                DryRunReguleringBody(
                    startDatoRegulering = gyldigMåned,
                    gjeldendeSatsFra = gyldigDato,
                    dryRunGrunnbeløp = DryRunGrunnbeløp(
                        virkningstidspunkt = gyldigDato,
                        ikrafttredelse = null,
                        grunnbeløp = 130160,
                        omregningsfaktor = mistenkelig,
                        garantipensjonOrdinær = 224248,
                        garantipensjonHøy = 242418,
                    ),
                ),
            ),
        ),
        UgyldigFeltCase("fraOgMedMåned", "$automatiskUri/omregning", Brukerrolle.Drift, serialize(AutomatiskKjøringBody(fraOgMedMåned = mistenkelig))),
        UgyldigFeltCase("saksnummer", "$automatiskUri/omregning/dry", Brukerrolle.Drift, serialize(DryRunOmregningBody(fraOgMedMåned = gyldigMåned, saksnummer = mistenkelig))),
    )

    @Test
    fun `avviser mistenkelig innhold i alle strengfelt uten å kalle reguleringsservicene`() {
        ugyldigeFelt.forEach { case ->
            val mocks = Mocks()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = mocks.services())
                }
                defaultRequest(HttpMethod.Post, case.uri, listOf(case.rolle)) {
                    setBody(case.body)
                }.apply {
                    withClue("${case.uri} - ${case.felt}") {
                        status shouldBe HttpStatusCode.BadRequest
                        deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe
                            ugyldigInput(UgyldigInputValideringsfeil(case.felt, forventetMelding))
                    }
                }
            }
            mocks.verifiserIngenKall()
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, "$reguleringUri/attestering/underkjenn", listOf(Brukerrolle.Attestant)) {
                setBody(serialize(UnderkjennReguleringBody(kommentar = "<b>hei</b>")))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("kommentar", "inneholder tegn utenfor tillatt tegnsett: '<>'"),
                )
            }
        }
    }

    @Test
    fun `avslutt godtar begrunnelse opp til maks fritekstlengde`() {
        val reguleringManuellServiceMock = mock<ReguleringManuellService> {
            on { avslutt(any(), any(), any()) } doReturn KunneIkkeAvslutte.FantIkkeRegulering.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(reguleringManuellService = reguleringManuellServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, "$reguleringUri/avslutt", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(AvsluttReguleringRequest(begrunnelse = "a".repeat(InputFelter.FRITEKST.maksLengde))))
            }.apply {
                // Kommer forbi valideringen og treffer service-mocken
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "fant_ikke_regulering"
            }
        }
    }

    @Test
    fun `avviser begrunnelse over maks fritekstlengde`() {
        val forLang = InputFelter.FRITEKST.maksLengde + 1
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, "$reguleringUri/avslutt", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(AvsluttReguleringRequest(begrunnelse = "a".repeat(forLang))))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil(
                        "begrunnelse",
                        "for lang verdi, antall tegn $forLang, maks ${InputFelter.FRITEKST.maksLengde}",
                    ),
                )
            }
        }
    }

    @Test
    fun `svarer ugyldig body uten å kalle servicen når bodyen ikke er json eller ikke passer DTO-en`() {
        val ikkeJson = "{ikke json"
        val manglerPåkrevdFelt = "{}"
        listOf(ikkeJson, manglerPåkrevdFelt).forEach { body ->
            val mocks = Mocks()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = mocks.services())
                }
                defaultRequest(HttpMethod.Post, "$reguleringUri/avslutt", listOf(Brukerrolle.Saksbehandler)) {
                    setBody(body)
                }.apply {
                    withClue(body) {
                        status shouldBe HttpStatusCode.BadRequest
                        bodyAsText() shouldContain "ugyldig_body"
                    }
                }
            }
            mocks.verifiserIngenKall()
        }
    }

    @Test
    fun `underkjenn med blank kommentar svarer ugyldig body uten å kalle servicen`() {
        val mocks = Mocks()
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = mocks.services())
            }
            defaultRequest(HttpMethod.Post, "$reguleringUri/attestering/underkjenn", listOf(Brukerrolle.Attestant)) {
                setBody(serialize(UnderkjennReguleringBody(kommentar = " ")))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "ugyldig_body"
            }
        }
        mocks.verifiserIngenKall()
    }
}
