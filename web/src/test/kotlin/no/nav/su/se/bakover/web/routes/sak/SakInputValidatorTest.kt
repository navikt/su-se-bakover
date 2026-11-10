package no.nav.su.se.bakover.web.routes.sak

import arrow.core.left
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
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.sak.KunneIkkeOppretteDokument
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.inputvalidation.InputFelter
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringFeilResponse
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringsfeil
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import person.domain.KunneIkkeHenteNavnForNavIdent
import java.util.UUID

/**
 * Verifiserer at strengverdiene i JSON-bodyen til sak-endepunktene saniteres før de når [SakService].
 * Hvert case gjør nøyaktig ett felt ugyldig, slik at et endepunkt som ikke validerer bodyen, gir rød test.
 */
internal class SakInputValidatorTest {

    private val sakId: UUID = UUID.randomUUID()
    private val sakUri = "$SAK_PATH/$sakId"
    private val fnr = "12345678911"

    private val mistenkelig = "javascript:alert(1)"
    private val forventetMelding = "inneholder mistenkelig innhold"

    private data class UgyldigFeltCase(
        val felt: String,
        val uri: String,
        val rolle: Brukerrolle,
        val body: String,
    )

    private fun søk(
        fnr: String? = null,
        type: String? = null,
        saksnummer: String? = null,
    ) = serialize(SøkSakBody(fnr = fnr, type = type, saksnummer = saksnummer))

    private fun adresse(
        adresselinje1: String = "Gate 1",
        adresselinje2: String? = "Oppgang A",
        adresselinje3: String? = "Leilighet 2",
        postnummer: String = "0123",
        poststed: String = "Oslo",
    ) = DistribueringsadresseBody(
        adresselinje1 = adresselinje1,
        adresselinje2 = adresselinje2,
        adresselinje3 = adresselinje3,
        postnummer = postnummer,
        poststed = poststed,
    )

    private fun dokument(
        tittel: String = "Tittel",
        fritekst: String = "Fritekst",
        adresse: DistribueringsadresseBody? = adresse(),
    ) = serialize(
        DokumentBody(
            tittel = tittel,
            fritekst = fritekst,
            adresse = adresse,
            distribusjonstype = Distribusjonstype.VEDTAK,
        ),
    )

    /** `distribusjonstype` er en enum i DTO-en, så en ugyldig streng må bygges utenom [DokumentBody]. */
    private val dokumentMedUgyldigDistribusjonstype = serialize(
        mapOf(
            "tittel" to "Tittel",
            "fritekst" to "Fritekst",
            "adresse" to null,
            "distribusjonstype" to mistenkelig,
        ),
    )

    private fun ugyldigInput(vararg feil: UgyldigInputValideringsfeil) = UgyldigInputValideringFeilResponse(
        message = "Ugyldig input",
        code = "ugyldig_felt_input",
        errors = feil.toList(),
    )

    private val dokumentUrier = listOf("$sakUri/fritekstDokument", "$sakUri/fritekstDokument/lagreOgSend")

    private val ugyldigeFelt = listOf(
        UgyldigFeltCase("fnr", "$SAK_PATH/søk/info/fnr", Brukerrolle.Veileder, serialize(SøkSakFnrBody(fnr = mistenkelig))),

        UgyldigFeltCase("fnr", "$SAK_PATH/søk/fnr", Brukerrolle.Saksbehandler, serialize(SøkSakFnrBody(fnr = mistenkelig))),

        UgyldigFeltCase("fnr", "$SAK_PATH/søk", Brukerrolle.Saksbehandler, søk(fnr = mistenkelig, type = Sakstype.UFØRE.value)),
        UgyldigFeltCase("type", "$SAK_PATH/søk", Brukerrolle.Saksbehandler, søk(fnr = fnr, type = mistenkelig)),
        UgyldigFeltCase("saksnummer", "$SAK_PATH/søk", Brukerrolle.Saksbehandler, søk(saksnummer = mistenkelig)),

        UgyldigFeltCase("fnr", "$SAK_PATH/info", Brukerrolle.Veileder, serialize(SakInfoBody(fnr = mistenkelig, sakstype = Sakstype.UFØRE.value))),
        UgyldigFeltCase("sakstype", "$SAK_PATH/info", Brukerrolle.Veileder, serialize(SakInfoBody(fnr = fnr, sakstype = mistenkelig))),

        UgyldigFeltCase("fnr", "$SAK_PATH/opprett", Brukerrolle.Saksbehandler, serialize(OpprettSakBody(fnr = mistenkelig, sakstype = Sakstype.UFØRE.value))),
        UgyldigFeltCase("sakstype", "$SAK_PATH/opprett", Brukerrolle.Saksbehandler, serialize(OpprettSakBody(fnr = fnr, sakstype = mistenkelig))),

        // Bodyen er en lokal klasse i ruten, så JSON-en skrives direkte
        UgyldigFeltCase("fraOgMed", "$sakUri/gjeldendeVedtaksdata", Brukerrolle.Saksbehandler, """{"fraOgMed":"$mistenkelig","tilOgMed":null}"""),
        UgyldigFeltCase("tilOgMed", "$sakUri/gjeldendeVedtaksdata", Brukerrolle.Saksbehandler, """{"fraOgMed":"2021-01-01","tilOgMed":"$mistenkelig"}"""),
    ) + dokumentUrier.flatMap { uri ->
        listOf(
            UgyldigFeltCase("tittel", uri, Brukerrolle.Saksbehandler, dokument(tittel = mistenkelig)),
            UgyldigFeltCase("fritekst", uri, Brukerrolle.Saksbehandler, dokument(fritekst = mistenkelig)),
            UgyldigFeltCase("adresse.adresselinje1", uri, Brukerrolle.Saksbehandler, dokument(adresse = adresse(adresselinje1 = mistenkelig))),
            UgyldigFeltCase("adresse.adresselinje2", uri, Brukerrolle.Saksbehandler, dokument(adresse = adresse(adresselinje2 = mistenkelig))),
            UgyldigFeltCase("adresse.adresselinje3", uri, Brukerrolle.Saksbehandler, dokument(adresse = adresse(adresselinje3 = mistenkelig))),
            UgyldigFeltCase("adresse.postnummer", uri, Brukerrolle.Saksbehandler, dokument(adresse = adresse(postnummer = mistenkelig))),
            UgyldigFeltCase("adresse.poststed", uri, Brukerrolle.Saksbehandler, dokument(adresse = adresse(poststed = mistenkelig))),
            UgyldigFeltCase("distribusjonstype", uri, Brukerrolle.Saksbehandler, dokumentMedUgyldigDistribusjonstype),
        )
    }

    @Test
    fun `avviser mistenkelig innhold i alle strengfelt uten å kalle SakService`() {
        ugyldigeFelt.forEach { case ->
            val sakServiceMock = mock<SakService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(sakService = sakServiceMock),
                    )
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
            verifyNoInteractions(sakServiceMock)
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        val sakServiceMock = mock<SakService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(sakService = sakServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, "$sakUri/fritekstDokument", listOf(Brukerrolle.Saksbehandler)) {
                setBody(dokument(fritekst = "<b>hei</b>"))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("fritekst", "inneholder tegn utenfor tillatt tegnsett: '<>'"),
                )
            }
        }
        verifyNoInteractions(sakServiceMock)
    }

    @Test
    fun `svarer ugyldig body uten å kalle SakService når bodyen ikke er json eller ikke passer DTO-en`() {
        val ikkeJson = "{ikke json"
        val manglerPåkrevdFelt = "{}"
        listOf(ikkeJson, manglerPåkrevdFelt).forEach { body ->
            val sakServiceMock = mock<SakService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(sakService = sakServiceMock),
                    )
                }
                defaultRequest(HttpMethod.Post, "$sakUri/fritekstDokument", listOf(Brukerrolle.Saksbehandler)) {
                    setBody(body)
                }.apply {
                    withClue(body) {
                        status shouldBe HttpStatusCode.BadRequest
                        bodyAsText() shouldContain "ugyldig_body"
                    }
                }
            }
            verifyNoInteractions(sakServiceMock)
        }
    }

    @Test
    fun `fritekstDokument godtar fritekst opp til maks fritekstlengde`() {
        val sakServiceMock = mock<SakService> {
            on { genererFritekstbrevPåSak(any()) } doReturn KunneIkkeOppretteDokument.FeilVedHentingAvSaksbehandlernavn(
                KunneIkkeHenteNavnForNavIdent.FantIkkeBrukerForNavIdent,
            ).left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(sakService = sakServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, "$sakUri/fritekstDokument", listOf(Brukerrolle.Saksbehandler)) {
                setBody(dokument(fritekst = "a".repeat(InputFelter.FRITEKST.maksLengde)))
            }.apply {
                // Kommer forbi valideringen og treffer service-mocken
                status shouldBe HttpStatusCode.NotFound
            }
        }
        verify(sakServiceMock).genererFritekstbrevPåSak(any())
    }

    @Test
    fun `fritekstDokument avviser fritekst over maks fritekstlengde`() {
        val sakServiceMock = mock<SakService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(sakService = sakServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, "$sakUri/fritekstDokument", listOf(Brukerrolle.Saksbehandler)) {
                setBody(dokument(fritekst = "a".repeat(InputFelter.FRITEKST.maksLengde + 1)))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil(
                        "fritekst",
                        "for lang verdi, antall tegn ${InputFelter.FRITEKST.maksLengde + 1}, maks ${InputFelter.FRITEKST.maksLengde}",
                    ),
                )
            }
        }
        verifyNoInteractions(sakServiceMock)
    }
}
