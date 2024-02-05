package no.nav.su.se.bakover.statistikk.behandling.søknadsbehandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTrukket
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

/**
 * Tester behandlingsstatistikk hendelser i forbindelse med søknadsbehandling
 */
internal class StatistikkSøknadsbehandlingTest {

    @Test
    fun `publiserer opprettet søknadsbehandling`() {
        val søknadsbehandling = nySøknadsbehandlingMedStønadsperiode().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.Opprettet(
                søknadsbehandling = søknadsbehandling,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomOpprettet"),
            ),
            behandlingStatus = "UNDER_BEHANDLING",
            behandlingStatusBeskrivelse = "Et mellomsteg i behandlingen.",
            funksjonellTid = søknadsbehandling.opprettet,
            behandlingYtelseDetaljer = "[]",
            saksbehandler = "saksbehandlerSomOpprettet",
        )
    }

    @Test
    fun `publiserer innvilget søknadsbehandling til attestering`() {
        val søknadsbehandling = søknadsbehandlingTilAttesteringInnvilget().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget(søknadsbehandling),
            behandlingStatus = "TIL_ATTESTERING",
            behandlingStatusBeskrivelse = "Saksbehandler har sendt behandlingen videre til beslutter/attestant/saksbehandler2 som må velge og enten underkjenne(sendes tilbake til saksbehandler) eller iverksette (ferdigbehandler) den.",
            resultat = "INNVILGET",
            resultatBeskrivelse = "Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering.",
            funksjonellTid = fixedTidspunkt,
        )
    }

    @Test
    fun `publiserer avslått søknadsbehandling uten beregning til attestering`() {
        val søknadsbehandling = søknadsbehandlingTilAttesteringAvslagUtenBeregning().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.TilAttestering.Avslag(søknadsbehandling),
            behandlingStatus = "TIL_ATTESTERING",
            behandlingStatusBeskrivelse = "Saksbehandler har sendt behandlingen videre til beslutter/attestant/saksbehandler2 som må velge og enten underkjenne(sendes tilbake til saksbehandler) eller iverksette (ferdigbehandler) den.",
            resultat = "AVSLÅTT",
            resultatBeskrivelse = "Gjelder kun søknadsbehandling: I henhold til lov om supplerende stønad blir en søknad avslått. Tilsvarende resultat for revurdering er opphørt.",
            resultatBegrunnelse = "INNLAGT_PÅ_INSTITUSJON",
            funksjonellTid = fixedTidspunkt,
        )
    }

    @Test
    fun `publiserer avslått søknadsbehandling med beregning til attestering`() {
        val søknadsbehandling = søknadsbehandlingTilAttesteringAvslagMedBeregning().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.TilAttestering.Avslag(søknadsbehandling),
            behandlingStatus = "TIL_ATTESTERING",
            behandlingStatusBeskrivelse = "Saksbehandler har sendt behandlingen videre til beslutter/attestant/saksbehandler2 som må velge og enten underkjenne(sendes tilbake til saksbehandler) eller iverksette (ferdigbehandler) den.",
            resultat = "AVSLÅTT",
            resultatBeskrivelse = "Gjelder kun søknadsbehandling: I henhold til lov om supplerende stønad blir en søknad avslått. Tilsvarende resultat for revurdering er opphørt.",
            resultatBegrunnelse = "FOR_HØY_INNTEKT",
            funksjonellTid = fixedTidspunkt,
        )
    }

    @Test
    fun `publiserer underkjent innvilget søknadsbehandling til attestering`() {
        val søknadsbehandling = søknadsbehandlingUnderkjentInnvilget().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget(søknadsbehandling),
            behandlingStatus = "UNDERKJENT",
            behandlingStatusBeskrivelse = "beslutter/attestant/saksbehandler2 har sendt saken tilbake til saksbehandler.",
            resultat = "INNVILGET",
            resultatBeskrivelse = "Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering.",
            funksjonellTid = fixedTidspunkt,
            beslutter = "attestant",
        )
    }

    @Test
    fun `publiserer underkjent avslått søknadsbehandling uten beregning til attestering`() {
        val søknadsbehandling = søknadsbehandlingUnderkjentAvslagUtenBeregning().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.Underkjent.Avslag(søknadsbehandling),
            behandlingStatus = "UNDERKJENT",
            behandlingStatusBeskrivelse = "beslutter/attestant/saksbehandler2 har sendt saken tilbake til saksbehandler.",
            resultat = "AVSLÅTT",
            resultatBeskrivelse = "Gjelder kun søknadsbehandling: I henhold til lov om supplerende stønad blir en søknad avslått. Tilsvarende resultat for revurdering er opphørt.",
            resultatBegrunnelse = "INNLAGT_PÅ_INSTITUSJON",
            funksjonellTid = fixedTidspunkt,
            beslutter = "attestant",
        )
    }

    @Test
    fun `publiserer underkjent avslått søknadsbehandling med beregning til attestering`() {
        val søknadsbehandling = søknadsbehandlingUnderkjentAvslagMedBeregning().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.Underkjent.Avslag(søknadsbehandling),
            behandlingStatus = "UNDERKJENT",
            behandlingStatusBeskrivelse = "beslutter/attestant/saksbehandler2 har sendt saken tilbake til saksbehandler.",
            resultat = "AVSLÅTT",
            resultatBeskrivelse = "Gjelder kun søknadsbehandling: I henhold til lov om supplerende stønad blir en søknad avslått. Tilsvarende resultat for revurdering er opphørt.",
            resultatBegrunnelse = "FOR_HØY_INNTEKT",
            funksjonellTid = fixedTidspunkt,
            beslutter = "attestant",
        )
    }

    @Test
    fun `publiserer iverksatt innvilget søknadsbehandling`() {
        val vedtak =
            vedtakSøknadsbehandlingIverksattInnvilget().first.vedtakListe.first() as VedtakInnvilgetSøknadsbehandling
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget(vedtak),
            behandlingStatus = "IVERKSATT",
            behandlingStatusBeskrivelse = "Behandlingen har blitt iverksatt.",
            resultat = "INNVILGET",
            resultatBeskrivelse = "Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering.",
            avsluttet = true,
            funksjonellTid = vedtak.opprettet,
            beslutter = "attestant",
        )
    }

    @Test
    fun `publiserer iverksatt avslått søknadsbehandling uten beregning`() {
        val (_, vedtak) = vedtakSøknadsbehandlingIverksattAvslagUtenBeregning()
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag(vedtak),
            behandlingStatus = "IVERKSATT",
            behandlingStatusBeskrivelse = "Behandlingen har blitt iverksatt.",
            resultat = "AVSLÅTT",
            resultatBeskrivelse = "Gjelder kun søknadsbehandling: I henhold til lov om supplerende stønad blir en søknad avslått. Tilsvarende resultat for revurdering er opphørt.",
            resultatBegrunnelse = "INNLAGT_PÅ_INSTITUSJON",
            avsluttet = true,
            funksjonellTid = vedtak.opprettet,
            beslutter = "attestant",
        )
    }

    @Test
    fun `publiserer iverksatt avslått søknadsbehandling med beregning`() {
        val (_, vedtak) = vedtakSøknadsbehandlingIverksattAvslagMedBeregning()
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag(vedtak),
            behandlingStatus = "IVERKSATT",
            behandlingStatusBeskrivelse = "Behandlingen har blitt iverksatt.",
            resultat = "AVSLÅTT",
            resultatBeskrivelse = "Gjelder kun søknadsbehandling: I henhold til lov om supplerende stønad blir en søknad avslått. Tilsvarende resultat for revurdering er opphørt.",
            resultatBegrunnelse = "FOR_HØY_INNTEKT",
            avsluttet = true,
            funksjonellTid = vedtak.opprettet,
            beslutter = "attestant",
        )
    }

    @Test
    fun `publiserer lukket søknadsbehandling`() {
        val (_, søknadsbehandling) = søknadsbehandlingTrukket(
            saksbehandlerSomLukket = NavIdentBruker.Saksbehandler("saksbehandlerSomAvsluttet"),
        )
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Søknad.Lukket(
                søknadsbehandling,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvsluttet"),
            ),
            behandlingStatus = "AVSLUTTET",
            behandlingStatusBeskrivelse = "Behandlingen/søknaden har blitt avsluttet/lukket.",
            resultat = "TRUKKET",
            resultatBeskrivelse = "Bruker eller verge/fullmakt har bedt om å trekke søknad/klage. En spesifisering av [AVBRUTT].",
            totrinnsbehandling = false,
            avsluttet = true,
            funksjonellTid = søknadsbehandling.lukketTidspunkt,
            saksbehandler = "saksbehandlerSomAvsluttet",
            behandlingYtelseDetaljer = "[]",
        )
    }

    private fun assert(
        statistikkEvent: StatistikkEvent.Behandling.Søknad,
        behandlingStatus: String,
        behandlingStatusBeskrivelse: String,
        resultat: String? = null,
        resultatBeskrivelse: String? = null,
        resultatBegrunnelse: String? = null,
        beslutter: String? = null,
        totrinnsbehandling: Boolean = true,
        avsluttet: Boolean = false,
        saksbehandler: String = "saksbehandler",
        funksjonellTid: Tidspunkt,
        behandlingYtelseDetaljer: String = """[
                         {
                           "satsgrunn":"BOR_ALENE"
                         }
                       ]""",
    ) {
        val kafkaPublisherMock: KafkaPublisher = mock()

        StatistikkEventObserverBuilder(
            kafkaPublisher = kafkaPublisherMock,
            personService = mock(),
            clock = fixedClock,
            gitCommit = GitCommit("87a3a5155bf00b4d6854efcc24e8b929549c9302"),
        ).statistikkService.handle(statistikkEvent)
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe "supstonad.aapen-su-behandling-statistikk-v1" },
            argThat {
                // language=JSON
                JSONAssert.assertEquals(
                    """
                    {
                       "funksjonellTid":"$funksjonellTid",
                       "tekniskTid":"2021-01-01T01:02:03.456789Z",
                       "mottattDato":"2021-01-01",
                       "registrertDato":"2021-01-01",
                       "behandlingId":${statistikkEvent.søknadsbehandling.id.value},
                       "sakId":${statistikkEvent.søknadsbehandling.sakId},
                       "søknadId":${statistikkEvent.søknadsbehandling.søknad.id},
                       "saksnummer":"12345676",
                       "behandlingType":"SOKNAD",
                       "behandlingTypeBeskrivelse":"Søknad for SU Uføre",
                       "behandlingStatus":"$behandlingStatus",
                       "behandlingStatusBeskrivelse":"$behandlingStatusBeskrivelse",
                       "behandlingYtelseDetaljer": $behandlingYtelseDetaljer,
                       "utenlandstilsnitt":"NASJONAL",
                       "ansvarligEnhetKode":"4815",
                       "ansvarligEnhetType":"NORG",
                       "behandlendeEnhetKode":"4815",
                       "behandlendeEnhetType":"NORG",
                       "avsender":"su-se-bakover",
                       "saksbehandler":"$saksbehandler",
                       ${if (resultat != null) """"resultat":"$resultat",""" else ""}
                       ${if (resultatBeskrivelse != null) """"resultatBeskrivelse":"$resultatBeskrivelse",""" else ""}
                       ${if (resultatBegrunnelse != null) """"resultatBegrunnelse":"$resultatBegrunnelse",""" else ""}
                       ${if (beslutter != null) """"beslutter":"$beslutter",""" else ""}
                       "avsluttet": $avsluttet,
                       "totrinnsbehandling": $totrinnsbehandling,
                       "versjon":"87a3a5155bf00b4d6854efcc24e8b929549c9302"
                    }
                    """.trimIndent(),
                    it,
                    CustomComparator(
                        JSONCompareMode.STRICT,
                        Customization(
                            "behandlingId",
                        ) { _, _ -> true },
                        Customization(
                            "sakId",
                        ) { _, _ -> true },
                        Customization(
                            "søknadId",
                        ) { _, _ -> true },
                    ),

                )
            },
        )
    }
}
