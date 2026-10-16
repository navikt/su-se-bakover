package no.nav.su.se.bakover.web.routes.klage

import arrow.core.left
import behandling.klage.domain.FormkravTilKlage
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.inputvalidation.InputFelter
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.time.LocalDate
import java.util.UUID

/**
 * Verifiserer at alle fritekstfelt i klage-endepunktene saniteres før de når [KlageService].
 * Hvert case gjør nøyaktig ett felt ugyldig, slik at et felt som mangler i valideringen gir rød test.
 */
internal class KlageinnholdInputValidatorTest {

    private val sakId: UUID = UUID.randomUUID()
    private val klageId: UUID = UUID.randomUUID()
    private val klagerUri = "$SAK_PATH/$sakId/klager"
    private val klageUri = "$klagerUri/$klageId"

    private val mistenkelig = "javascript:alert(1)"
    private val forventetMelding = "inneholder mistenkelig innhold"

    private data class UgyldigFeltCase(
        val felt: String,
        val uri: String,
        val rolle: Brukerrolle,
        val body: String,
    )

    private fun opprett(erInfotrygdSakId: String?) = serialize(
        OpprettKlageRequest(
            journalpostId = "12345678",
            datoKlageMottatt = LocalDate.of(2026, 1, 1),
            relatertBehandlingId = null,
            erInfotrygdSakId = erInfotrygdSakId,
        ),
    )

    private fun vilkårsvurder(
        innenforFristen: String? = null,
        klagesDetPåKonkreteElementerIVedtaket: String? = null,
        erUnderskrevet: String? = null,
        fremsattRettsligKlageinteresse: String? = null,
        infotrygdSakId: String? = null,
    ) = serialize(
        VilkårsvurderKlageBody(
            vedtakId = null,
            innenforFristen = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, innenforFristen),
            klagesDetPåKonkreteElementerIVedtaket = FormkravTilKlage.BooleanMedBegrunnelse(
                true,
                klagesDetPåKonkreteElementerIVedtaket,
            ),
            erUnderskrevet = FormkravTilKlage.SvarMedBegrunnelse(FormkravTilKlage.Svarord.JA, erUnderskrevet),
            fremsattRettsligKlageinteresse = FormkravTilKlage.SvarMedBegrunnelse(
                FormkravTilKlage.Svarord.JA,
                fremsattRettsligKlageinteresse,
            ),
            infotrygdSakId = infotrygdSakId,
        ),
    )

    private fun vurder(
        fritekstTilBrev: String? = null,
        omgjørBegrunnelse: String? = null,
        delvisEgenInstansBegrunnelse: String? = null,
        opprettholdKlagenotat: String? = null,
        delvisKaKlagenotat: String? = null,
    ) = serialize(
        VurderKlageBody(
            fritekstTilBrev = fritekstTilBrev,
            omgjør = BehandlesIVedtaksinstansBody(årsak = null, begrunnelse = omgjørBegrunnelse),
            delvisomgjøring_egen_instans = BehandlesIVedtaksinstansBody(
                årsak = null,
                begrunnelse = delvisEgenInstansBegrunnelse,
            ),
            oppretthold = SkalTilKabalBody(klagenotat = opprettholdKlagenotat),
            delvisomgjøringKa = SkalTilKabalBody(klagenotat = delvisKaKlagenotat),
        ),
    )

    private val ugyldigeFelt = listOf(
        UgyldigFeltCase("erInfotrygdSakId", klagerUri, Brukerrolle.Saksbehandler, opprett(erInfotrygdSakId = mistenkelig)),

        UgyldigFeltCase("innenforFristen.begrunnelse", "$klageUri/vilkår/vurderinger", Brukerrolle.Saksbehandler, vilkårsvurder(innenforFristen = mistenkelig)),
        UgyldigFeltCase("klagesDetPåKonkreteElementerIVedtaket.begrunnelse", "$klageUri/vilkår/vurderinger", Brukerrolle.Saksbehandler, vilkårsvurder(klagesDetPåKonkreteElementerIVedtaket = mistenkelig)),
        UgyldigFeltCase("erUnderskrevet.begrunnelse", "$klageUri/vilkår/vurderinger", Brukerrolle.Saksbehandler, vilkårsvurder(erUnderskrevet = mistenkelig)),
        UgyldigFeltCase("fremsattRettsligKlageinteresse.begrunnelse", "$klageUri/vilkår/vurderinger", Brukerrolle.Saksbehandler, vilkårsvurder(fremsattRettsligKlageinteresse = mistenkelig)),
        UgyldigFeltCase("infotrygdSakId", "$klageUri/vilkår/vurderinger", Brukerrolle.Saksbehandler, vilkårsvurder(infotrygdSakId = mistenkelig)),

        UgyldigFeltCase("fritekst", "$klageUri/avvist/fritekstTilBrev", Brukerrolle.Saksbehandler, serialize(AvvistKlageFritekstBody(fritekst = mistenkelig))),

        UgyldigFeltCase("fritekstTilBrev", "$klageUri/vurderinger", Brukerrolle.Saksbehandler, vurder(fritekstTilBrev = mistenkelig)),
        UgyldigFeltCase("omgjør.begrunnelse", "$klageUri/vurderinger", Brukerrolle.Saksbehandler, vurder(omgjørBegrunnelse = mistenkelig)),
        UgyldigFeltCase("delvisomgjøring_egen_instans.begrunnelse", "$klageUri/vurderinger", Brukerrolle.Saksbehandler, vurder(delvisEgenInstansBegrunnelse = mistenkelig)),
        UgyldigFeltCase("oppretthold.klagenotat", "$klageUri/vurderinger", Brukerrolle.Saksbehandler, vurder(opprettholdKlagenotat = mistenkelig)),
        UgyldigFeltCase("delvisomgjøringKa.klagenotat", "$klageUri/vurderinger", Brukerrolle.Saksbehandler, vurder(delvisKaKlagenotat = mistenkelig)),

        UgyldigFeltCase("grunn", "$klageUri/underkjenn", Brukerrolle.Attestant, serialize(KlageUnderkjentBody(grunn = mistenkelig, kommentar = "ok"))),
        UgyldigFeltCase("kommentar", "$klageUri/underkjenn", Brukerrolle.Attestant, serialize(KlageUnderkjentBody(grunn = "ANDRE_FORHOLD", kommentar = mistenkelig))),

        UgyldigFeltCase("begrunnelse", "$klageUri/avslutt", Brukerrolle.Saksbehandler, serialize(AvsluttKlageBody(begrunnelse = mistenkelig))),
    )

    @Test
    fun `avviser mistenkelig innhold i alle fritekstfelt uten å kalle KlageService`() {
        ugyldigeFelt.forEach { case ->
            val klageServiceMock = mock<KlageService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services().copy(klageService = klageServiceMock),
                    )
                }
                defaultRequest(HttpMethod.Post, case.uri, listOf(case.rolle)) {
                    setBody(case.body)
                }.apply {
                    withClue("${case.uri} - ${case.felt}") {
                        status shouldBe HttpStatusCode.BadRequest
                        deserialize<ErrorJson>(bodyAsText()) shouldBe ErrorJson(
                            message = "${case.felt}: $forventetMelding",
                            code = "ugyldig_felt_input",
                        )
                    }
                }
            }
            verifyNoInteractions(klageServiceMock)
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, "$klageUri/vilkår/vurderinger", listOf(Brukerrolle.Saksbehandler)) {
                setBody(vilkårsvurder(erUnderskrevet = "<b>hei</b>"))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<ErrorJson>(bodyAsText()).message shouldBe "erUnderskrevet.begrunnelse: inneholder tegn utenfor tillatt tegnsett: '<>'"
            }
        }
    }

    @Test
    fun `slår sammen flere feil i én melding`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, "$klageUri/vurderinger", listOf(Brukerrolle.Saksbehandler)) {
                setBody(vurder(fritekstTilBrev = mistenkelig, opprettholdKlagenotat = "hei\u0000"))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<ErrorJson>(bodyAsText()).message shouldBe
                    "fritekstTilBrev: $forventetMelding, oppretthold.klagenotat: inneholder kontrolltegn: '\u0000'"
            }
        }
    }

    @Test
    fun `avslutt godtar begrunnelse opp til maks fritekstlengde`() {
        val klageServiceMock = mock<KlageService> {
            on { avslutt(any(), any(), any()) } doReturn KunneIkkeAvslutteKlage.FantIkkeKlage.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, "$klageUri/avslutt", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(AvsluttKlageBody(begrunnelse = "a".repeat(InputFelter.FRITEKST.maksLengde))))
            }.apply {
                // Kommer forbi valideringen og treffer service-mocken
                status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `avviser begrunnelse over maks fritekstlengde`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, "$klageUri/avslutt", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(AvsluttKlageBody(begrunnelse = "a".repeat(InputFelter.FRITEKST.maksLengde + 1))))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<ErrorJson>(bodyAsText()).message shouldBe
                    "begrunnelse: for lang verdi, antall tegn ${InputFelter.FRITEKST.maksLengde + 1}, maks ${InputFelter.FRITEKST.maksLengde}"
            }
        }
    }

    @Test
    fun `godtar norske bokstaver, tillatte skilletegn og linjeskift`() {
        val gyldigTekst = "ÆØÅ æøå. Hei, går det bra? (ja) 50% § 3-2 «sitat» – \"ok\"\nNy linje"
        val klageServiceMock = mock<KlageService> {
            on { vilkårsvurder(any()) } doReturn KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, "$klageUri/vilkår/vurderinger", listOf(Brukerrolle.Saksbehandler)) {
                setBody(
                    vilkårsvurder(
                        innenforFristen = gyldigTekst,
                        klagesDetPåKonkreteElementerIVedtaket = gyldigTekst,
                        erUnderskrevet = gyldigTekst,
                        fremsattRettsligKlageinteresse = gyldigTekst,
                        infotrygdSakId = "IT-12345",
                    ),
                )
            }.apply {
                // Kommer forbi valideringen og treffer service-mocken
                status shouldBe HttpStatusCode.NotFound
            }
        }
    }
}
