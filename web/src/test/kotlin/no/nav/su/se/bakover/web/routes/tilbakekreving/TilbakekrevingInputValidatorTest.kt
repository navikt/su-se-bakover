package no.nav.su.se.bakover.web.routes.tilbakekreving

import arrow.core.left
import dokument.domain.Brevtype
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.infrastructure.web.withUser
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.mottaker.DistribueringsadresseRequest
import no.nav.su.se.bakover.domain.mottaker.LagreMottaker
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.web.configureAuthentication
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.inputvalidation.InputFelter
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringFeilResponse
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringsfeil
import no.nav.su.se.bakover.web.routes.tilbakekreving.avslutt.AvbrytTilbakekrevingsbehandlingBody
import no.nav.su.se.bakover.web.routes.tilbakekreving.forhåndsvarsel.ForhåndsvisBody
import no.nav.su.se.bakover.web.routes.tilbakekreving.notat.TkNotatRequest
import no.nav.su.se.bakover.web.routes.tilbakekreving.opprett.OpprettTilbakekrevingRequest
import no.nav.su.se.bakover.web.routes.tilbakekreving.vedtaksbrev.BrevtekstBody
import no.nav.su.se.bakover.web.routes.tilbakekreving.vurder.TilbakekrevingRequest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import tilbakekreving.application.service.avbrutt.AvbrytTilbakekrevingsbehandlingService
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingService
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService
import tilbakekreving.application.service.iverksett.IverksettTilbakekrevingService
import tilbakekreving.application.service.kravgrunnlag.AnnullerKravgrunnlagService
import tilbakekreving.application.service.kravgrunnlag.OppdaterKravgrunnlagService
import tilbakekreving.application.service.notat.NotatTilbakekrevingsbehandlingService
import tilbakekreving.application.service.opprett.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.application.service.tilAttestering.TilbakekrevingsbehandlingTilAttesteringService
import tilbakekreving.application.service.underkjenn.UnderkjennTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.BrevTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.MånedsvurderingerTilbakekrevingsbehandlingService
import tilbakekreving.domain.avbrudd.AvbrytTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.avbrudd.KunneIkkeAvbryte
import tilbakekreving.domain.underkjennelse.UnderkjennAttesteringsgrunnTilbakekreving
import java.util.UUID
import no.nav.su.se.bakover.web.routes.tilbakekreving.forhåndsvarsel.Body as ForhåndsvarselBody
import no.nav.su.se.bakover.web.routes.tilbakekreving.underkjenn.Body as UnderkjennBody

internal class TilbakekrevingInputValidatorTest {
    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val grunnlagId = UUID.randomUUID()
    private val baseUri = "/saker/$sakId/tilbakekreving"
    private val behandlingUri = "$baseUri/$behandlingId"
    private val versjon = 1L
    private val maksLengde = InputFelter.FRITEKST.maksLengde
    private val mistenkelig = "javascript:alert(1)"
    private val periode = PeriodeJson("2026-01-01", "2026-01-31")
    private val grunn = UnderkjennAttesteringsgrunnTilbakekreving.entries.first().name
    private val mottaker = LagreMottaker(
        navn = "Testmottaker",
        orgnummer = "123456789",
        adresse = DistribueringsadresseRequest(
            adresselinje1 = "Testveien 1",
            adresselinje2 = "Andre etasje",
            adresselinje3 = "Postboks 1",
            postnummer = "0001",
            poststed = "Oslo",
        ),
        referanseId = behandlingId.toString(),
        referanseType = ReferanseTypeMottaker.DØDSBO_TILBAKEKREVING.name,
        brevtype = Brevtype.FORHANDSVARSEL.name,
    )

    private data class Endepunkt(
        val uri: String,
        val rolle: Brukerrolle = Brukerrolle.Saksbehandler,
        val metode: HttpMethod = HttpMethod.Post,
    )

    private data class FeltCase(
        val felt: String,
        val endepunkt: Endepunkt,
        val body: (String) -> String,
    )

    private val opprett = Endepunkt("$baseUri/ny")
    private val avbryt = Endepunkt("$behandlingUri/avbryt")
    private val notat = Endepunkt("$behandlingUri/notat")
    private val underkjenn = Endepunkt("$behandlingUri/underkjenn", Brukerrolle.Attestant)
    private val brevtekst = Endepunkt("$behandlingUri/brevtekst")
    private val forhåndsvarsel = Endepunkt("$behandlingUri/forhandsvarsel")
    private val forhåndsvis = Endepunkt("$behandlingUri/forhandsvarsel/forhandsvis")
    private val vurder = Endepunkt("$behandlingUri/vurder")
    private val endepunkter = listOf(
        opprett, avbryt, notat, underkjenn, brevtekst, forhåndsvarsel, forhåndsvis, vurder,
        Endepunkt("$behandlingUri/tilAttestering"),
        Endepunkt("$behandlingUri/iverksett", Brukerrolle.Attestant),
        Endepunkt("$behandlingUri/oppdaterKravgrunnlag"),
        Endepunkt("$baseUri/kravgrunnlag/$grunnlagId/annuller", metode = HttpMethod.Patch),
    )

    private fun vurderBody(
        vurdering: String = "SkalTilbakekreve",
        periode: PeriodeJson = this.periode,
    ) = serialize(
        TilbakekrevingRequest(versjon, listOf(TilbakekrevingRequest.ForPeriode(periode, vurdering))),
    )

    private fun mottakerCase(
        felt: String,
        endre: (LagreMottaker, String) -> LagreMottaker,
    ) = FeltCase("dødsbo.$felt", forhåndsvarsel) { verdi ->
        serialize(ForhåndsvarselBody(versjon, "Fritekst", endre(mottaker, verdi)))
    }

    private val felt = listOf(
        FeltCase("relatertId", opprett) { verdi -> serialize(OpprettTilbakekrevingRequest(versjon, verdi)) },
        FeltCase("begrunnelse", avbryt) { verdi -> serialize(AvbrytTilbakekrevingsbehandlingBody(versjon, verdi)) },
        FeltCase("notat", notat) { verdi -> serialize(TkNotatRequest(versjon, verdi)) },
        FeltCase("kommentar", underkjenn) { verdi -> serialize(UnderkjennBody(versjon, verdi, grunn)) },
        FeltCase("grunn", underkjenn) { verdi -> serialize(UnderkjennBody(versjon, "Kommentar", verdi)) },
        FeltCase("brevtekst", brevtekst) { verdi -> serialize(BrevtekstBody(versjon, verdi)) },
        FeltCase("fritekst", forhåndsvarsel) { verdi -> serialize(ForhåndsvarselBody(versjon, verdi, null)) },
        FeltCase("fritekst", forhåndsvis) { verdi -> serialize(ForhåndsvisBody(versjon, verdi, false)) },
        FeltCase("perioder[0].vurdering", vurder) { verdi -> vurderBody(vurdering = verdi) },
        FeltCase("perioder[0].periode.fraOgMed", vurder) { verdi -> vurderBody(periode = periode.copy(fraOgMed = verdi)) },
        FeltCase("perioder[0].periode.tilOgMed", vurder) { verdi -> vurderBody(periode = periode.copy(tilOgMed = verdi)) },
        mottakerCase("navn") { mottaker, verdi -> mottaker.copy(navn = verdi) },
        mottakerCase("foedselsnummer") { mottaker, verdi -> mottaker.copy(foedselsnummer = verdi, orgnummer = null) },
        mottakerCase("orgnummer") { mottaker, verdi -> mottaker.copy(orgnummer = verdi) },
        mottakerCase("referanseId") { mottaker, verdi -> mottaker.copy(referanseId = verdi) },
        mottakerCase("referanseType") { mottaker, verdi -> mottaker.copy(referanseType = verdi) },
        mottakerCase("brevtype") { mottaker, verdi -> mottaker.copy(brevtype = verdi) },
        mottakerCase("adresse.adresselinje1") { mottaker, verdi -> mottaker.copy(adresse = mottaker.adresse.copy(adresselinje1 = verdi)) },
        mottakerCase("adresse.adresselinje2") { mottaker, verdi -> mottaker.copy(adresse = mottaker.adresse.copy(adresselinje2 = verdi)) },
        mottakerCase("adresse.adresselinje3") { mottaker, verdi -> mottaker.copy(adresse = mottaker.adresse.copy(adresselinje3 = verdi)) },
        mottakerCase("adresse.postnummer") { mottaker, verdi -> mottaker.copy(adresse = mottaker.adresse.copy(postnummer = verdi)) },
        mottakerCase("adresse.poststed") { mottaker, verdi -> mottaker.copy(adresse = mottaker.adresse.copy(poststed = verdi)) },
    )

    private class Tjenester {
        val opprett = mock<OpprettTilbakekrevingsbehandlingService>()
        val avbryt = mock<AvbrytTilbakekrevingsbehandlingService> {
            on { avbryt(any()) } doReturn KunneIkkeAvbryte.UlikVersjon.left()
        }
        val notat = mock<NotatTilbakekrevingsbehandlingService>()
        val underkjenn = mock<UnderkjennTilbakekrevingsbehandlingService>()
        val brevtekst = mock<BrevTilbakekrevingsbehandlingService>()
        val forhåndsvarsel = mock<ForhåndsvarsleTilbakekrevingsbehandlingService>()
        val forhåndsvis = mock<ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService>()
        val vurder = mock<MånedsvurderingerTilbakekrevingsbehandlingService>()
        val tilAttestering = mock<TilbakekrevingsbehandlingTilAttesteringService>()
        val iverksett = mock<IverksettTilbakekrevingService>()
        val oppdaterKravgrunnlag = mock<OppdaterKravgrunnlagService>()
        val annuller = mock<AnnullerKravgrunnlagService>()

        fun Application.medRuter() {
            val config = applicationConfig()
            configureAuthentication(AzureClientStub, config)
            routing {
                authenticate("jwt") {
                    withUser(config) {
                        tilbakekrevingRoutes(
                            opprettTilbakekrevingsbehandlingService = opprett,
                            månedsvurderingerTilbakekrevingsbehandlingService = vurder,
                            brevTilbakekrevingsbehandlingService = brevtekst,
                            forhåndsvisVedtaksbrevTilbakekrevingsbehandlingService = mock(),
                            forhåndsvarsleTilbakekrevingsbehandlingService = forhåndsvarsel,
                            forhåndsvisForhåndsvarselTilbakekrevingsbehandlingService = forhåndsvis,
                            visUtsendtForhåndsvarselbrevForTilbakekrevingService = mock(),
                            tilbakekrevingsbehandlingTilAttesteringService = tilAttestering,
                            underkjennTilbakekrevingsbehandlingService = underkjenn,
                            iverksettTilbakekrevingService = iverksett,
                            avbrytTilbakekrevingsbehandlingService = avbryt,
                            oppdaterKravgrunnlagService = oppdaterKravgrunnlag,
                            notatTilbakekrevingsbehandlingService = notat,
                            annullerKravgrunnlagService = annuller,
                        )
                    }
                }
            }
        }

        fun ingenKall() = verifyNoInteractions(
            opprett, avbryt, notat, underkjenn, brevtekst, forhåndsvarsel,
            forhåndsvis, vurder, tilAttestering, iverksett, oppdaterKravgrunnlag, annuller,
        )
    }

    private fun avvisFelt(case: FeltCase, verdi: String, melding: String) {
        val tjenester = Tjenester()
        testApplication {
            application { with(tjenester) { medRuter() } }
            defaultRequest(case.endepunkt.metode, case.endepunkt.uri, listOf(case.endepunkt.rolle)) {
                setBody(case.body(verdi))
            }.apply {
                withClue("${case.endepunkt.uri}: ${case.felt}") {
                    status shouldBe HttpStatusCode.BadRequest
                    deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe UgyldigInputValideringFeilResponse(
                        message = "Ugyldig input",
                        code = "ugyldig_felt_input",
                        errors = listOf(UgyldigInputValideringsfeil(case.felt, melding)),
                    )
                }
            }
        }
        tjenester.ingenKall()
    }

    @Test
    fun `avviser mistenkelig innhold i hvert strengfelt før tjenestekall`() {
        felt.forEach { case -> avvisFelt(case, mistenkelig, "inneholder mistenkelig innhold") }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene`() {
        avvisFelt(felt.first { case -> case.endepunkt == notat }, "<b>hei</b>", "inneholder tegn utenfor tillatt tegnsett: '<>'")
    }

    @Test
    fun `avviser alle strengfelt over maks lengde`() {
        val forLang = "a".repeat(maksLengde + 1)
        felt.forEach { case ->
            avvisFelt(case, forLang, "for lang verdi, antall tegn ${forLang.length}, maks $maksLengde")
        }
    }

    @Test
    fun `avviser ugyldig json og manglende påkrevde felt på alle json-ruter`() {
        val tjenester = Tjenester()
        testApplication {
            application { with(tjenester) { medRuter() } }
            endepunkter.forEach { endepunkt ->
                listOf("{ikke json", "{}").forEach { body ->
                    defaultRequest(endepunkt.metode, endepunkt.uri, listOf(endepunkt.rolle)) {
                        setBody(body)
                    }.apply {
                        withClue("${endepunkt.uri}: $body") {
                            status shouldBe HttpStatusCode.BadRequest
                            deserialize<ErrorJson>(bodyAsText()).code shouldBe "ugyldig_body"
                        }
                    }
                }
            }
        }
        tjenester.ingenKall()
    }

    @Test
    fun `godtar begrunnelse akkurat på lengdegrensen og norske tegn`() {
        val tjenester = Tjenester()
        val begrunnelser = listOf(
            "a".repeat(maksLengde),
            "ÆØÅ æøå. Hei, går det bra? (ja) 50% § 3-2 «sitat» – \"ok\"\nNy linje",
        )
        testApplication {
            application { with(tjenester) { medRuter() } }
            begrunnelser.forEach { begrunnelse ->
                defaultRequest(avbryt.metode, avbryt.uri, listOf(avbryt.rolle)) {
                    setBody(serialize(AvbrytTilbakekrevingsbehandlingBody(versjon, begrunnelse)))
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                    deserialize<ErrorJson>(bodyAsText()).code shouldBe "utdatert_versjon"
                }
            }
        }
        val commands = argumentCaptor<AvbrytTilbakekrevingsbehandlingCommand>()
        verify(tjenester.avbryt, times(begrunnelser.size)).avbryt(commands.capture())
        commands.allValues.map { command -> command.begrunnelse } shouldBe begrunnelser
        commands.allValues.forEach { command ->
            command.sakId shouldBe sakId
            command.behandlingsId.value shouldBe behandlingId
        }
    }

    @Test
    fun `beholder feilsvaret for blankt notat`() {
        val tjenester = Tjenester()
        testApplication {
            application { with(tjenester) { medRuter() } }
            defaultRequest(notat.metode, notat.uri, listOf(notat.rolle)) {
                setBody(serialize(TkNotatRequest(versjon, " ")))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<ErrorJson>(bodyAsText()).code shouldBe "notat_mangler_innhold"
            }
        }
        tjenester.ingenKall()
    }
}
