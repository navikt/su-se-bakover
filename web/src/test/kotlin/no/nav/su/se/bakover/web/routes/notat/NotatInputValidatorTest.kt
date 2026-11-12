package no.nav.su.se.bakover.web.routes.notat

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
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.notat.NotatFeil
import no.nav.su.se.bakover.domain.notat.NotatService
import no.nav.su.se.bakover.domain.notat.ReferanseType
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
import org.mockito.kotlin.verifyNoInteractions
import java.util.UUID

/**
 * Verifiserer at strengverdiene i JSON-bodyen til notat-endepunktene saniteres før de når [NotatService].
 * Hvert case gjør nøyaktig ett felt ugyldig, slik at et endepunkt som ikke validerer bodyen, gir rød test.
 */
internal class NotatInputValidatorTest {

    private val sakId: UUID = UUID.randomUUID()
    private val notatId: UUID = UUID.randomUUID()
    private val notatUri = "$NOTAT_PATH/$sakId"
    private val saksbehandlerUri = "$notatUri/$notatId/saksbehandler"
    private val attestantUri = "$notatUri/$notatId/attestant"

    private val mistenkelig = "javascript:alert(1)"
    private val forventetMelding = "inneholder mistenkelig innhold"

    private data class OpprettNotatRequest(
        val referanseId: String,
        val referanseType: String,
    )

    private data class OppdaterNotatRequest(
        val notat: String,
    )

    private data class UgyldigFeltCase(
        val felt: String,
        val uri: String,
        val rolle: Brukerrolle,
        val body: String,
    )

    private fun opprett(
        referanseId: String = UUID.randomUUID().toString(),
        referanseType: String = ReferanseType.SØKNADSBEHANDLING.name,
    ) = serialize(OpprettNotatRequest(referanseId = referanseId, referanseType = referanseType))

    private fun oppdater(notat: String) = serialize(OppdaterNotatRequest(notat = notat))

    private fun ugyldigInput(vararg feil: UgyldigInputValideringsfeil) = UgyldigInputValideringFeilResponse(
        message = "Ugyldig input",
        code = "ugyldig_felt_input",
        errors = feil.toList(),
    )

    private val ugyldigeFelt = listOf(
        UgyldigFeltCase("referanseId", notatUri, Brukerrolle.Saksbehandler, opprett(referanseId = mistenkelig)),
        UgyldigFeltCase("referanseType", notatUri, Brukerrolle.Saksbehandler, opprett(referanseType = mistenkelig)),
        UgyldigFeltCase("notat", saksbehandlerUri, Brukerrolle.Saksbehandler, oppdater(mistenkelig)),
        UgyldigFeltCase("notat", attestantUri, Brukerrolle.Attestant, oppdater(mistenkelig)),
    )

    @Test
    fun `avviser mistenkelig innhold i alle strengfelt uten å kalle NotatService`() {
        ugyldigeFelt.forEach { case ->
            val notatServiceMock = mock<NotatService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services().copy(notatService = notatServiceMock),
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
            verifyNoInteractions(notatServiceMock)
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, saksbehandlerUri, listOf(Brukerrolle.Saksbehandler)) {
                setBody(oppdater("<b>hei</b>"))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("notat", "inneholder tegn utenfor tillatt tegnsett: '<>'"),
                )
            }
        }
    }

    @Test
    fun `svarer ugyldig body uten å kalle NotatService når bodyen ikke er json eller ikke passer DTO-en`() {
        val ikkeJson = "{ikke json"
        val manglerPåkrevdFelt = "{}"
        listOf(ikkeJson, manglerPåkrevdFelt).forEach { body ->
            val notatServiceMock = mock<NotatService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services().copy(notatService = notatServiceMock),
                    )
                }
                defaultRequest(HttpMethod.Post, saksbehandlerUri, listOf(Brukerrolle.Saksbehandler)) {
                    setBody(body)
                }.apply {
                    withClue(body) {
                        status shouldBe HttpStatusCode.BadRequest
                        bodyAsText() shouldContain "ugyldig_body"
                    }
                }
            }
            verifyNoInteractions(notatServiceMock)
        }
    }

    @Test
    fun `godtar notat opp til maks fritekstlengde`() {
        val notatServiceMock = mock<NotatService> {
            on { oppdaterNotatSaksbehandler(any(), any(), any(), any(), any()) } doReturn NotatFeil.FantIkkeNotat.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(notatService = notatServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, saksbehandlerUri, listOf(Brukerrolle.Saksbehandler)) {
                setBody(oppdater("a".repeat(InputFelter.FRITEKST.maksLengde)))
            }.apply {
                // Kommer forbi valideringen og treffer service-mocken
                status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `avviser notat over maks fritekstlengde`() {
        val forLangLengde = InputFelter.FRITEKST.maksLengde + 1
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, saksbehandlerUri, listOf(Brukerrolle.Saksbehandler)) {
                setBody(oppdater("a".repeat(forLangLengde)))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil(
                        "notat",
                        "for lang verdi, antall tegn $forLangLengde, maks ${InputFelter.FRITEKST.maksLengde}",
                    ),
                )
            }
        }
    }
}
