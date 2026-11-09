package no.nav.su.se.bakover.web.routes.historisk

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
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsbosituasjon
import no.nav.su.se.bakover.service.historisk.SupstonadHistoriskService
import no.nav.su.se.bakover.service.historisk.revurdering.HistoriskInfotrygdRevurderingService
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
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.KunneIkkeHentePerson
import person.domain.PersonService
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.util.UUID

/**
 * Verifiserer at strengverdiene i JSON-bodyen til de historiske endepunktene saniteres før de når servicene.
 * Hvert case gjør nøyaktig ett felt ugyldig, slik at et endepunkt som ikke validerer bodyen, gir rød test.
 *
 * Endepunkter med `{revurderingId}` slår opp revurderingen før bodyen leses, så for dem er
 * [HistoriskInfotrygdRevurderingService.hentMedSakInfo] det eneste tillatte kallet.
 */
internal class HistoriskInputValidatorTest {

    private val fnr = "12345678910"
    private val revurderingId = HistoriskInfotrygdRevurderingId(UUID.randomUUID())
    private val revurderingerUri = "$HISTORISK_ALDERSSAK_PATH/revurderinger"
    private val revurderingUri = "$revurderingerUri/${revurderingId.value}"

    private val mistenkelig = "javascript:alert(1)"
    private val forventetMelding = "inneholder mistenkelig innhold"

    private data class UgyldigFeltCase(
        val felt: String,
        val uri: String,
        val rolle: Brukerrolle,
        val body: String,
        val slårOppRevurdering: Boolean = true,
    )

    private fun String?.tilJson() = this?.let { "\"$it\"" } ?: "null"

    private fun fnrBody(fnr: String = this.fnr) = """{"fnr":"$fnr"}"""

    private fun opprett(
        fnr: String = this.fnr,
        fraOgMed: String = "2021-01-01",
        tilOgMed: String = "2021-12-31",
    ) = """{"fnr":"$fnr","periode":{"fraOgMed":"$fraOgMed","tilOgMed":"$tilOgMed"}}"""

    private fun vedtaksbrev(
        valg: String = HistoriskInfotrygdVedtaksbrevvalgRequest.SEND.name,
        fritekst: String? = "Fritekst",
    ) = """{"valg":"$valg","fritekst":${fritekst.tilJson()}}"""

    private fun beregn(
        måned: String = "2021-01",
        type: String = "Arbeidsinntekt",
        beskrivelse: String? = null,
        valuta: String? = null,
        bosituasjon: String = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG.name,
    ): String {
        val utenlandskInntekt = valuta?.let {
            """{"beløpIUtenlandskValuta":100,"valuta":"$it","kurs":1.5}"""
        }
        return """
            {"måneder":[{
              "måned":"$måned",
              "fradrag":[{
                "type":"$type",
                "beskrivelse":${beskrivelse.tilJson()},
                "månedsbeløp":1000.0,
                "utenlandskInntekt":$utenlandskInntekt,
                "tilhører":"${FradragTilhører.BRUKER.name}"
              }],
              "manueltOpphør":null,
              "bosituasjon":"$bosituasjon"
            }]}
        """.trimIndent()
    }

    private fun ugyldigInput(vararg feil: UgyldigInputValideringsfeil) = UgyldigInputValideringFeilResponse(
        message = "Ugyldig input",
        code = "ugyldig_felt_input",
        errors = feil.toList(),
    )

    private val ugyldigeFelt = listOf(
        UgyldigFeltCase("fnr", "$HISTORISK_ALDERSSAK_PATH/finnes", Brukerrolle.Saksbehandler, fnrBody(mistenkelig), slårOppRevurdering = false),
        UgyldigFeltCase("fnr", "$HISTORISK_ALDERSSAK_PATH/vedtaksperioder", Brukerrolle.Attestant, fnrBody(mistenkelig), slårOppRevurdering = false),

        UgyldigFeltCase("fnr", revurderingerUri, Brukerrolle.Saksbehandler, opprett(fnr = mistenkelig), slårOppRevurdering = false),
        UgyldigFeltCase("periode.fraOgMed", revurderingerUri, Brukerrolle.Saksbehandler, opprett(fraOgMed = mistenkelig), slårOppRevurdering = false),
        UgyldigFeltCase("periode.tilOgMed", revurderingerUri, Brukerrolle.Saksbehandler, opprett(tilOgMed = mistenkelig), slårOppRevurdering = false),
        UgyldigFeltCase("fnr", "$revurderingerUri/oversikt", Brukerrolle.Saksbehandler, fnrBody(mistenkelig), slårOppRevurdering = false),

        UgyldigFeltCase("valg", "$revurderingUri/vedtaksbrev", Brukerrolle.Saksbehandler, vedtaksbrev(valg = mistenkelig)),
        UgyldigFeltCase("fritekst", "$revurderingUri/vedtaksbrev", Brukerrolle.Saksbehandler, vedtaksbrev(fritekst = mistenkelig)),

        UgyldigFeltCase("måneder[0].måned", "$revurderingUri/beregning", Brukerrolle.Saksbehandler, beregn(måned = mistenkelig)),
        UgyldigFeltCase("måneder[0].fradrag[0].type", "$revurderingUri/beregning", Brukerrolle.Saksbehandler, beregn(type = mistenkelig)),
        UgyldigFeltCase("måneder[0].fradrag[0].beskrivelse", "$revurderingUri/beregning", Brukerrolle.Saksbehandler, beregn(beskrivelse = mistenkelig)),
        UgyldigFeltCase("måneder[0].fradrag[0].utenlandskInntekt.valuta", "$revurderingUri/beregning", Brukerrolle.Saksbehandler, beregn(valuta = mistenkelig)),
        UgyldigFeltCase("måneder[0].bosituasjon", "$revurderingUri/beregning", Brukerrolle.Saksbehandler, beregn(bosituasjon = mistenkelig)),

        UgyldigFeltCase("fritekst", "$revurderingUri/forhandsvarsel/utkast", Brukerrolle.Saksbehandler, serialize(HistoriskInfotrygdForhåndsvarselRequest(fritekst = mistenkelig))),
        UgyldigFeltCase("fritekst", "$revurderingUri/forhandsvarsel/send", Brukerrolle.Saksbehandler, serialize(HistoriskInfotrygdForhåndsvarselRequest(fritekst = mistenkelig))),

        UgyldigFeltCase("begrunnelse", "$revurderingUri/underkjenn", Brukerrolle.Attestant, serialize(BegrunnelseRequest(begrunnelse = mistenkelig))),
        UgyldigFeltCase("begrunnelse", "$revurderingUri/avslutt", Brukerrolle.Saksbehandler, serialize(BegrunnelseRequest(begrunnelse = mistenkelig))),
    )

    private fun revurderingServiceMedRevurdering(): HistoriskInfotrygdRevurderingService = mock {
        on { hentMedSakInfo(revurderingId) } doReturn Pair(
            SakInfo(UUID.randomUUID(), Saksnummer(2021), Fnr(fnr), Sakstype.ALDER),
            mock<HistoriskInfotrygdRevurdering>(),
        )
    }

    private fun personServiceUtenTilgang(): PersonService = mock {
        on { sjekkTilgangTilPerson(any(), any()) } doReturn KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
    }

    @Test
    fun `avviser mistenkelig innhold i alle strengfelt uten å kalle servicene`() {
        ugyldigeFelt.forEach { case ->
            val revurderingService = revurderingServiceMedRevurdering()
            val personService = mock<PersonService>()
            val supstonadHistoriskService = mock<SupstonadHistoriskService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            person = personService,
                            supstonadHistoriskService = supstonadHistoriskService,
                            historiskInfotrygdRevurderingService = revurderingService,
                        ),
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
            if (case.slårOppRevurdering) {
                verify(revurderingService).hentMedSakInfo(revurderingId)
            }
            verifyNoMoreInteractions(revurderingService)
            verifyNoInteractions(personService, supstonadHistoriskService)
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        historiskInfotrygdRevurderingService = revurderingServiceMedRevurdering(),
                    ),
                )
            }
            defaultRequest(HttpMethod.Post, "$revurderingUri/avslutt", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(BegrunnelseRequest(begrunnelse = "<b>hei</b>")))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("begrunnelse", "inneholder tegn utenfor tillatt tegnsett: '<>'"),
                )
            }
        }
    }

    @Test
    fun `godtar gyldige verdier i strengfelt som ikke er fritekst`() {
        val personService = personServiceUtenTilgang()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        person = personService,
                        historiskInfotrygdRevurderingService = revurderingServiceMedRevurdering(),
                    ),
                )
            }
            defaultRequest(HttpMethod.Post, "$revurderingUri/beregning", listOf(Brukerrolle.Saksbehandler)) {
                setBody(
                    beregn(
                        type = "Annet",
                        beskrivelse = "Inntekt fra utlandet",
                        valuta = "SEK",
                        bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67_UFØR_FLYKTNING.name,
                    ),
                )
            }.apply {
                // Kommer forbi valideringen og toCommand, og stopper på tilgangssjekken
                status shouldBe HttpStatusCode.Forbidden
            }
        }
        verify(personService).sjekkTilgangTilPerson(Fnr(fnr), Sakstype.ALDER)
    }

    @Test
    fun `svarer ugyldig body uten å kalle servicene når bodyen ikke er json eller ikke passer DTO-en`() {
        val ikkeJson = "{ikke json"
        val manglerPåkrevdFelt = "{}"
        listOf(ikkeJson, manglerPåkrevdFelt).forEach { body ->
            val revurderingService = mock<HistoriskInfotrygdRevurderingService>()
            val personService = mock<PersonService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            person = personService,
                            historiskInfotrygdRevurderingService = revurderingService,
                        ),
                    )
                }
                defaultRequest(HttpMethod.Post, revurderingerUri, listOf(Brukerrolle.Saksbehandler)) {
                    setBody(body)
                }.apply {
                    withClue(body) {
                        status shouldBe HttpStatusCode.BadRequest
                        bodyAsText() shouldContain "ugyldig_body"
                    }
                }
            }
            verifyNoInteractions(revurderingService, personService)
        }
    }

    @Test
    fun `avslutt godtar begrunnelse opp til maks fritekstlengde`() {
        val personService = personServiceUtenTilgang()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        person = personService,
                        historiskInfotrygdRevurderingService = revurderingServiceMedRevurdering(),
                    ),
                )
            }
            defaultRequest(HttpMethod.Post, "$revurderingUri/avslutt", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(BegrunnelseRequest(begrunnelse = "a".repeat(InputFelter.FRITEKST.maksLengde))))
            }.apply {
                // Kommer forbi valideringen og stopper på tilgangssjekken
                status shouldBe HttpStatusCode.Forbidden
            }
        }
        verify(personService).sjekkTilgangTilPerson(Fnr(fnr), Sakstype.ALDER)
    }

    @Test
    fun `avviser begrunnelse over maks fritekstlengde`() {
        val personService = mock<PersonService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        person = personService,
                        historiskInfotrygdRevurderingService = revurderingServiceMedRevurdering(),
                    ),
                )
            }
            defaultRequest(HttpMethod.Post, "$revurderingUri/avslutt", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(BegrunnelseRequest(begrunnelse = "a".repeat(InputFelter.FRITEKST.maksLengde + 1))))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil(
                        "begrunnelse",
                        "for lang verdi, antall tegn ${InputFelter.FRITEKST.maksLengde + 1}, maks ${InputFelter.FRITEKST.maksLengde}",
                    ),
                )
            }
        }
        verifyNoInteractions(personService)
    }
}
