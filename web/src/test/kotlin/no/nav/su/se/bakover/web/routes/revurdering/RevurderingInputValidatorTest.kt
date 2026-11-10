package no.nav.su.se.bakover.web.routes.revurdering

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
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseService
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.revurdering.underkjenn.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.domain.revurdering.vilkår.formue.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
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
 * Verifiserer at strengverdiene i JSON-bodyen til revurdering-endepunktene saniteres før de når servicene.
 * Hvert case gjør nøyaktig ett felt ugyldig, slik at et endepunkt som ikke validerer bodyen, gir rød test.
 */
internal class RevurderingInputValidatorTest {

    private val sakId: UUID = UUID.randomUUID()
    private val revurderingId: UUID = UUID.randomUUID()
    private val revurderingerUri = "/saker/$sakId/revurderinger"
    private val revurderingUri = "$revurderingerUri/$revurderingId"

    private val mistenkelig = "javascript:alert(1)"
    private val forventetMelding = "inneholder mistenkelig innhold"

    private data class UgyldigFeltCase(
        val felt: String,
        val metode: HttpMethod,
        val uri: String,
        val body: String,
        val rolle: Brukerrolle = Brukerrolle.Saksbehandler,
    )

    private fun json(vararg felt: Pair<String, Any?>) = serialize(mapOf(*felt))
    private fun liste(vararg felt: Pair<String, Any?>) = serialize(listOf(mapOf(*felt)))

    private fun ugyldigInput(vararg feil: UgyldigInputValideringsfeil) = UgyldigInputValideringFeilResponse(
        message = "Ugyldig input",
        code = "ugyldig_felt_input",
        errors = feil.toList(),
    )

    private val periode = mapOf("fraOgMed" to "2021-05-01", "tilOgMed" to "2021-12-31")
    private val formueverdier = mapOf(
        "verdiIkkePrimærbolig" to 0,
        "verdiEiendommer" to 0,
        "verdiKjøretøy" to 0,
        "innskudd" to 0,
        "verdipapir" to 0,
        "pengerSkyldt" to 0,
        "kontanter" to 0,
        "depositumskonto" to 0,
    )

    private fun formue(begrunnelse: String) = liste(
        "periode" to periode,
        "epsFormue" to null,
        "søkersFormue" to formueverdier,
        "begrunnelse" to begrunnelse,
    )

    private val ugyldigeFelt = listOf(
        UgyldigFeltCase("årsak", HttpMethod.Post, revurderingerUri, json("årsak" to mistenkelig)),
        UgyldigFeltCase("omgjøringsgrunn", HttpMethod.Post, revurderingerUri, json("omgjøringsgrunn" to mistenkelig)),
        UgyldigFeltCase("klageId", HttpMethod.Post, revurderingerUri, json("klageId" to mistenkelig)),
        UgyldigFeltCase("informasjonSomRevurderes[0]", HttpMethod.Post, revurderingerUri, json("informasjonSomRevurderes" to listOf(mistenkelig))),

        UgyldigFeltCase("årsak", HttpMethod.Put, revurderingUri, json("årsak" to mistenkelig)),
        UgyldigFeltCase("omgjøringsgrunn", HttpMethod.Put, revurderingUri, json("omgjøringsgrunn" to mistenkelig)),

        UgyldigFeltCase("valg", HttpMethod.Post, "$revurderingUri/brevvalg", json("valg" to mistenkelig)),

        UgyldigFeltCase("bosituasjoner[0].epsFnr", HttpMethod.Post, "$revurderingUri/bosituasjongrunnlag", json("bosituasjoner" to listOf(mapOf("epsFnr" to mistenkelig)))),
        UgyldigFeltCase("vurderinger[0].begrunnelse", HttpMethod.Post, "$revurderingUri/uføregrunnlag", json("vurderinger" to listOf(mapOf("begrunnelse" to mistenkelig)))),
        UgyldigFeltCase("[0].begrunnelse", HttpMethod.Post, "$revurderingUri/formuegrunnlag", formue(begrunnelse = mistenkelig)),

        UgyldigFeltCase("fradrag[0].type", HttpMethod.Post, "$revurderingUri/fradrag", json("fradrag" to listOf(mapOf("type" to mistenkelig)))),
        UgyldigFeltCase("fradrag[0].beskrivelse", HttpMethod.Post, "$revurderingUri/fradrag", json("fradrag" to listOf(mapOf("beskrivelse" to mistenkelig)))),
        UgyldigFeltCase("fradrag[0].tilhører", HttpMethod.Post, "$revurderingUri/fradrag", json("fradrag" to listOf(mapOf("tilhører" to mistenkelig)))),
        UgyldigFeltCase("fradrag[0].utenlandskInntekt.valuta", HttpMethod.Post, "$revurderingUri/fradrag", json("fradrag" to listOf(mapOf("utenlandskInntekt" to mapOf("valuta" to mistenkelig))))),

        UgyldigFeltCase("vurderinger[0].resultat", HttpMethod.Post, "$revurderingUri/familiegjenforening", json("vurderinger" to listOf(mapOf("resultat" to mistenkelig)))),
        UgyldigFeltCase("[0].vurdering", HttpMethod.Post, "$revurderingUri/fastopphold", liste("vurdering" to mistenkelig)),
        UgyldigFeltCase("[0].vurdering", HttpMethod.Post, "$revurderingUri/flyktning", liste("vurdering" to mistenkelig)),
        UgyldigFeltCase("[0].pensjonsopplysninger.folketrygd", HttpMethod.Post, "$revurderingUri/pensjon", liste("pensjonsopplysninger" to mapOf("folketrygd" to mistenkelig))),
        UgyldigFeltCase("[0].vurdering", HttpMethod.Post, "$revurderingUri/personligoppmøte", liste("vurdering" to mistenkelig)),
        UgyldigFeltCase("vurderingsperioder[0].vurdering", HttpMethod.Post, "$revurderingUri/institusjonsopphold", json("vurderingsperioder" to listOf(mapOf("vurdering" to mistenkelig)))),

        UgyldigFeltCase("grunn", HttpMethod.Patch, "$revurderingUri/underkjenn", json("grunn" to mistenkelig, "kommentar" to "ok"), Brukerrolle.Attestant),
        UgyldigFeltCase("kommentar", HttpMethod.Patch, "$revurderingUri/underkjenn", json("grunn" to "ANDRE_FORHOLD", "kommentar" to mistenkelig), Brukerrolle.Attestant),

        UgyldigFeltCase("årsak", HttpMethod.Post, "$revurderingerUri/gjenoppta", json("årsak" to mistenkelig, "begrunnelse" to "ok")),
        UgyldigFeltCase("begrunnelse", HttpMethod.Post, "$revurderingerUri/gjenoppta", json("årsak" to "MOTTATT_KONTROLLERKLÆRING", "begrunnelse" to mistenkelig)),
        UgyldigFeltCase("begrunnelse", HttpMethod.Patch, "$revurderingerUri/gjenoppta/$revurderingId", json("årsak" to "MOTTATT_KONTROLLERKLÆRING", "begrunnelse" to mistenkelig)),

        UgyldigFeltCase("årsak", HttpMethod.Post, "$revurderingerUri/stans", json("fraOgMed" to "2021-05-01", "årsak" to mistenkelig, "begrunnelse" to "ok")),
        UgyldigFeltCase("begrunnelse", HttpMethod.Patch, "$revurderingerUri/stans/$revurderingId", json("fraOgMed" to "2021-05-01", "årsak" to "MANGLENDE_KONTROLLERKLÆRING", "begrunnelse" to mistenkelig)),

        UgyldigFeltCase("begrunnelse", HttpMethod.Post, "$revurderingUri/avslutt", json("begrunnelse" to mistenkelig, "fritekst" to null, "brevvalg" to null)),
        UgyldigFeltCase("fritekst", HttpMethod.Post, "$revurderingUri/avslutt", json("begrunnelse" to "ok", "fritekst" to mistenkelig, "brevvalg" to "SKAL_SENDE_BREV_MED_FRITEKST")),
        UgyldigFeltCase("fritekst", HttpMethod.Post, "$revurderingUri/brevutkastForAvslutting", json("fritekst" to mistenkelig)),

        UgyldigFeltCase("fritekst", HttpMethod.Post, "$revurderingUri/forhandsvarsel", json("fritekst" to mistenkelig)),
        UgyldigFeltCase("fritekst", HttpMethod.Post, "$revurderingUri/brevutkastForForhandsvarsel", json("fritekst" to mistenkelig)),
    )

    @Test
    fun `avviser mistenkelig innhold i alle strengfelt uten å kalle servicene`() {
        ugyldigeFelt.forEach { case ->
            val revurderingServiceMock = mock<RevurderingService>()
            val gjenopptakServiceMock = mock<GjenopptaYtelseService>()
            val stansServiceMock = mock<StansYtelseService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            revurdering = revurderingServiceMock,
                            gjenopptakAvYtelseService = gjenopptakServiceMock,
                            stansAvYtelseService = stansServiceMock,
                        ),
                    )
                }
                defaultRequest(case.metode, case.uri, listOf(case.rolle)) {
                    setBody(case.body)
                }.apply {
                    withClue("${case.metode.value} ${case.uri} - ${case.felt}") {
                        status shouldBe HttpStatusCode.BadRequest
                        deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe
                            ugyldigInput(UgyldigInputValideringsfeil(case.felt, forventetMelding))
                    }
                }
            }
            verifyNoInteractions(revurderingServiceMock, gjenopptakServiceMock, stansServiceMock)
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        val stansServiceMock = mock<StansYtelseService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(stansAvYtelseService = stansServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, "$revurderingerUri/stans", listOf(Brukerrolle.Saksbehandler)) {
                setBody(json("fraOgMed" to "2021-05-01", "årsak" to "MANGLENDE_KONTROLLERKLÆRING", "begrunnelse" to "<b>hei</b>{}"))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("begrunnelse", "inneholder tegn utenfor tillatt tegnsett: '<>{}'"),
                )
            }
        }
        verifyNoInteractions(stansServiceMock)
    }

    @Test
    fun `underkjenn godtar kommentar på maks lengde`() {
        val revurderingServiceMock = mock<RevurderingService> {
            on { underkjenn(any(), any()) } doReturn KunneIkkeUnderkjenneRevurdering.FantIkkeRevurdering.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(revurdering = revurderingServiceMock),
                )
            }
            defaultRequest(HttpMethod.Patch, "$revurderingUri/underkjenn", listOf(Brukerrolle.Attestant)) {
                setBody(json("grunn" to "ANDRE_FORHOLD", "kommentar" to "a".repeat(2000)))
            }.apply {
                // Kommer forbi valideringen og treffer service-mocken
                status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `underkjenn avviser kommentar over maks lengde`() {
        val revurderingServiceMock = mock<RevurderingService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(revurdering = revurderingServiceMock),
                )
            }
            defaultRequest(HttpMethod.Patch, "$revurderingUri/underkjenn", listOf(Brukerrolle.Attestant)) {
                setBody(json("grunn" to "ANDRE_FORHOLD", "kommentar" to "a".repeat(2001)))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("kommentar", "for lang verdi, antall tegn 2001, maks 2000"),
                )
            }
        }
        verifyNoInteractions(revurderingServiceMock)
    }

    @Test
    fun `formuegrunnlag godtar begrunnelse på maks lengde i liste på toppnivå`() {
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilFormuegrunnlag(any()) } doReturn KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(revurdering = revurderingServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, "$revurderingUri/formuegrunnlag", listOf(Brukerrolle.Saksbehandler)) {
                setBody(formue(begrunnelse = "a".repeat(2000)))
            }.apply {
                // Kommer forbi valideringen og treffer service-mocken
                status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `formuegrunnlag avviser begrunnelse over maks lengde i liste på toppnivå`() {
        val revurderingServiceMock = mock<RevurderingService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(revurdering = revurderingServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, "$revurderingUri/formuegrunnlag", listOf(Brukerrolle.Saksbehandler)) {
                setBody(formue(begrunnelse = "a".repeat(2001)))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("[0].begrunnelse", "for lang verdi, antall tegn 2001, maks 2000"),
                )
            }
        }
        verifyNoInteractions(revurderingServiceMock)
    }

    @Test
    fun `svarer ugyldig body uten å kalle servicen når bodyen ikke er json eller ikke passer DTO-en`() {
        val ikkeJson = "{ikke json"
        val manglerPåkrevdFelt = "{}"
        listOf("$revurderingUri/avslutt", "$revurderingUri/formuegrunnlag").forEach { uri ->
            listOf(ikkeJson, manglerPåkrevdFelt).forEach { body ->
                val revurderingServiceMock = mock<RevurderingService>()
                testApplication {
                    application {
                        testSusebakoverWithMockedDb(
                            services = TestServicesBuilder.services(revurdering = revurderingServiceMock),
                        )
                    }
                    defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)) {
                        setBody(body)
                    }.apply {
                        withClue("$uri - $body") {
                            status shouldBe HttpStatusCode.BadRequest
                            bodyAsText() shouldContain "ugyldig_body"
                        }
                    }
                }
                verifyNoInteractions(revurderingServiceMock)
            }
        }
    }
}
