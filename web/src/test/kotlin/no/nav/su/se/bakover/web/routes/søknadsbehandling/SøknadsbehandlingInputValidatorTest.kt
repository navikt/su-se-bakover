package no.nav.su.se.bakover.web.routes.søknadsbehandling

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
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
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
 * Verifiserer at strengverdiene i JSON-bodyen til søknadsbehandling-endepunktene saniteres før de når servicen.
 * Hvert case gjør nøyaktig ett felt ugyldig, slik at et endepunkt som ikke validerer bodyen, gir rød test.
 */
internal class SøknadsbehandlingInputValidatorTest {

    private val sakId: UUID = UUID.randomUUID()
    private val behandlingId: UUID = UUID.randomUUID()
    private val behandlingerUri = "/saker/$sakId/behandlinger"
    private val behandlingUri = "$behandlingerUri/$behandlingId"

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

    private fun services(søknadsbehandlingService: SøknadsbehandlingService) = TestServicesBuilder.services(
        søknadsbehandling = SøknadsbehandlingServices(
            søknadsbehandlingService = søknadsbehandlingService,
            iverksettSøknadsbehandlingService = mock(),
        ),
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
        "måInnhenteMerInformasjon" to false,
    )

    private val ugyldigeFelt = listOf(
        UgyldigFeltCase("soknadId", HttpMethod.Post, behandlingerUri, json("soknadId" to mistenkelig)),
        UgyldigFeltCase("periode.fraOgMed", HttpMethod.Post, "$behandlingUri/stønadsperiode", json("periode" to mapOf("fraOgMed" to mistenkelig, "tilOgMed" to "2021-12-31"), "harSaksbehandlerAvgjort" to false)),

        UgyldigFeltCase("valg", HttpMethod.Post, "$behandlingUri/brevvalg", json("valg" to mistenkelig)),

        UgyldigFeltCase("bosituasjoner[0].epsFnr", HttpMethod.Post, "$behandlingUri/grunnlag/bosituasjon", json("bosituasjoner" to listOf(mapOf("epsFnr" to mistenkelig)))),
        UgyldigFeltCase("vurderinger[0].begrunnelse", HttpMethod.Post, "$behandlingUri/uføregrunnlag", json("vurderinger" to listOf(mapOf("begrunnelse" to mistenkelig)))),
        UgyldigFeltCase("[0].begrunnelse", HttpMethod.Post, "$behandlingUri/formuegrunnlag", formue(begrunnelse = mistenkelig)),

        UgyldigFeltCase("fradrag[0].type", HttpMethod.Post, "$behandlingUri/fradrag", json("fradrag" to listOf(mapOf("type" to mistenkelig)))),
        UgyldigFeltCase("fradrag[0].beskrivelse", HttpMethod.Post, "$behandlingUri/fradrag", json("fradrag" to listOf(mapOf("beskrivelse" to mistenkelig)))),
        UgyldigFeltCase("fradrag[0].tilhører", HttpMethod.Post, "$behandlingUri/fradrag", json("fradrag" to listOf(mapOf("tilhører" to mistenkelig)))),
        UgyldigFeltCase("fradrag[0].utenlandskInntekt.valuta", HttpMethod.Post, "$behandlingUri/fradrag", json("fradrag" to listOf(mapOf("utenlandskInntekt" to mapOf("valuta" to mistenkelig))))),

        UgyldigFeltCase("vurderinger[0].resultat", HttpMethod.Post, "$behandlingUri/familiegjenforening", json("vurderinger" to listOf(mapOf("resultat" to mistenkelig)))),
        UgyldigFeltCase("[0].vurdering", HttpMethod.Post, "$behandlingUri/fastopphold", liste("vurdering" to mistenkelig)),
        UgyldigFeltCase("[0].vurdering", HttpMethod.Post, "$behandlingUri/flyktning", liste("vurdering" to mistenkelig)),
        UgyldigFeltCase("[0].pensjonsopplysninger.folketrygd", HttpMethod.Post, "$behandlingUri/pensjon", liste("pensjonsopplysninger" to mapOf("folketrygd" to mistenkelig))),
        UgyldigFeltCase("[0].vurdering", HttpMethod.Post, "$behandlingUri/personligoppmøte", liste("vurdering" to mistenkelig)),
        UgyldigFeltCase("vurderingsperioder[0].vurdering", HttpMethod.Post, "$behandlingUri/institusjonsopphold", json("vurderingsperioder" to listOf(mapOf("vurdering" to mistenkelig)))),

        UgyldigFeltCase("fra", HttpMethod.Post, "$behandlingUri/samletSkattegrunnlag", json("fra" to mistenkelig, "til" to "2021")),
        UgyldigFeltCase("til", HttpMethod.Post, "$behandlingUri/samletSkattegrunnlag", json("fra" to "2021", "til" to mistenkelig)),

        UgyldigFeltCase("grunn", HttpMethod.Patch, "$behandlingUri/underkjenn", json("grunn" to mistenkelig, "kommentar" to "ok"), Brukerrolle.Attestant),
        UgyldigFeltCase("kommentar", HttpMethod.Patch, "$behandlingUri/underkjenn", json("grunn" to "ANDRE_FORHOLD", "kommentar" to mistenkelig), Brukerrolle.Attestant),
    )

    @Test
    fun `avviser mistenkelig innhold i alle strengfelt uten å kalle servicen`() {
        ugyldigeFelt.forEach { case ->
            val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = services(søknadsbehandlingServiceMock))
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
            verifyNoInteractions(søknadsbehandlingServiceMock)
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services(søknadsbehandlingServiceMock))
            }
            defaultRequest(HttpMethod.Patch, "$behandlingUri/underkjenn", listOf(Brukerrolle.Attestant)) {
                setBody(json("grunn" to "ANDRE_FORHOLD", "kommentar" to "<b>hei</b>{}"))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("kommentar", "inneholder tegn utenfor tillatt tegnsett: '<>{}'"),
                )
            }
        }
        verifyNoInteractions(søknadsbehandlingServiceMock)
    }

    @Test
    fun `underkjenn godtar kommentar på maks lengde`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { underkjenn(any()) } doReturn KunneIkkeUnderkjenneSøknadsbehandling.FantIkkeBehandling.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services(søknadsbehandlingServiceMock))
            }
            defaultRequest(HttpMethod.Patch, "$behandlingUri/underkjenn", listOf(Brukerrolle.Attestant)) {
                setBody(json("grunn" to "ANDRE_FORHOLD", "kommentar" to "a".repeat(2000)))
            }.apply {
                // Kommer forbi valideringen og treffer service-mocken
                status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `underkjenn avviser kommentar over maks lengde`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services(søknadsbehandlingServiceMock))
            }
            defaultRequest(HttpMethod.Patch, "$behandlingUri/underkjenn", listOf(Brukerrolle.Attestant)) {
                setBody(json("grunn" to "ANDRE_FORHOLD", "kommentar" to "a".repeat(2001)))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("kommentar", "for lang verdi, antall tegn 2001, maks 2000"),
                )
            }
        }
        verifyNoInteractions(søknadsbehandlingServiceMock)
    }

    @Test
    fun `underkjenn uten gyldig grunn svarer fortsatt mangler_begrunnelse`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services(søknadsbehandlingServiceMock))
            }
            defaultRequest(HttpMethod.Patch, "$behandlingUri/underkjenn", listOf(Brukerrolle.Attestant)) {
                setBody(json("grunn" to "UKJENT_GRUNN", "kommentar" to "ok"))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "mangler_begrunnelse"
            }
        }
        verifyNoInteractions(søknadsbehandlingServiceMock)
    }

    @Test
    fun `formuegrunnlag godtar begrunnelse på maks lengde i liste på toppnivå`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilFormuevilkår(any()) } doReturn
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.FormueBosituasjonKonsistensfeil("feil").left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services(søknadsbehandlingServiceMock))
            }
            defaultRequest(HttpMethod.Post, "$behandlingUri/formuegrunnlag", listOf(Brukerrolle.Saksbehandler)) {
                setBody(formue(begrunnelse = "a".repeat(2000)))
            }.apply {
                // Kommer forbi valideringen og treffer service-mocken
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "feil_med_formue_eller_bosituasjon"
            }
        }
    }

    @Test
    fun `formuegrunnlag avviser begrunnelse over maks lengde i liste på toppnivå`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services(søknadsbehandlingServiceMock))
            }
            defaultRequest(HttpMethod.Post, "$behandlingUri/formuegrunnlag", listOf(Brukerrolle.Saksbehandler)) {
                setBody(formue(begrunnelse = "a".repeat(2001)))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("[0].begrunnelse", "for lang verdi, antall tegn 2001, maks 2000"),
                )
            }
        }
        verifyNoInteractions(søknadsbehandlingServiceMock)
    }

    @Test
    fun `svarer ugyldig body uten å kalle servicen når bodyen ikke er json eller ikke passer DTO-en`() {
        val ikkeJson = "{ikke json"
        val manglerPåkrevdFelt = "{}"
        listOf(
            HttpMethod.Post to behandlingerUri,
            HttpMethod.Post to "$behandlingUri/fradrag",
            HttpMethod.Patch to "$behandlingUri/underkjenn",
        ).forEach { (metode, uri) ->
            listOf(ikkeJson, manglerPåkrevdFelt).forEach { body ->
                val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService>()
                testApplication {
                    application {
                        testSusebakoverWithMockedDb(services = services(søknadsbehandlingServiceMock))
                    }
                    defaultRequest(metode, uri, listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant)) {
                        setBody(body)
                    }.apply {
                        withClue("$uri - $body") {
                            status shouldBe HttpStatusCode.BadRequest
                            bodyAsText() shouldContain "ugyldig_body"
                        }
                    }
                }
                verifyNoInteractions(søknadsbehandlingServiceMock)
            }
        }
    }
}
