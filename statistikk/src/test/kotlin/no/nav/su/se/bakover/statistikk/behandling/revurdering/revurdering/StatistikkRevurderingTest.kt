package no.nav.su.se.bakover.statistikk.behandling.revurdering.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.underkjentOpphørtRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert

internal class StatistikkRevurderingTest {

    @Test
    fun `publiserer opprettet revurdering`() {
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Opprettet(
                revurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second,
            ),
            behandlingStatus = "REGISTRERT",
            behandlingStatusBeskrivelse = "Opprettet/registrert behandling. Dette er ofte trigget av en saksbehandler.",
            behandlingYtelseDetaljer = "[]",
        )
    }

    @Test
    fun `publiserer innvilget revurdering til attestering`() {
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget(
                revurdering = tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second,
            ),
            behandlingStatus = "TIL_ATTESTERING",
            behandlingStatusBeskrivelse = "Saksbehandler har sendt behandlingene videre til beslutter/attestant/saksbehandler2 som må velge å underkjenne(sendes tilbake til saksbehandler) eller iverksette (ferdigbehandlet) den.",
            resultat = "Innvilget",
            resultatBeskrivelse = "Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering.",
        )
    }

    @Test
    fun `publiserer opphørt revurdering til attestering`() {

        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.TilAttestering.Opphør(
                revurdering = tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().second,
            ),
            behandlingStatus = "TIL_ATTESTERING",
            behandlingStatusBeskrivelse = "Saksbehandler har sendt behandlingene videre til beslutter/attestant/saksbehandler2 som må velge å underkjenne(sendes tilbake til saksbehandler) eller iverksette (ferdigbehandlet) den.",
            resultat = "Opphør",
            resultatBeskrivelse = "En revurdering kan opphøre, mens en søknadsbehandling kan bli avslått.",
            resultatBegrunnelse = "UFØRHET",
        )
    }

    @Test
    fun `publiserer innvilget underkjent revurdering`() {
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Underkjent.Innvilget(
                revurdering = underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second,
            ),
            behandlingStatus = "UNDERKJENT",
            behandlingStatusBeskrivelse = "beslutter/attestant/saksbehandler2 har sendt saken tilbake til saksbehandler.",
            resultat = "Innvilget",
            resultatBeskrivelse = "Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering.",
            beslutter = "attestant",
        )
    }

    @Test
    fun `publiserer opphørt underkjent revurdering`() {
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Underkjent.Opphør(
                revurdering = underkjentOpphørtRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second,
            ),
            behandlingStatus = "UNDERKJENT",
            behandlingStatusBeskrivelse = "beslutter/attestant/saksbehandler2 har sendt saken tilbake til saksbehandler.",
            resultat = "Opphør",
            resultatBeskrivelse = "En revurdering kan opphøre, mens en søknadsbehandling kan bli avslått.",
            resultatBegrunnelse = "UFØRHET",
            beslutter = "attestant",
        )
    }

    @Test
    fun `publiserer iverksatt innvilget revurdering`() {
        val (_, vedtak) = vedtakRevurderingIverksattInnvilget()
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Iverksatt.Innvilget(
                vedtak = vedtak,
            ),
            behandlingStatus = "IVERKSATT",
            behandlingStatusBeskrivelse = "Behandlingen har blitt iverksatt.",
            resultat = "Innvilget",
            resultatBeskrivelse = "Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering.",
            beslutter = "attestant",
            avsluttet = true,
        )
    }

    @Test
    fun `publiserer iverksatt opphørt revurdering`() {
        val (_, vedtak) = vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak()
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt(
                vedtak = vedtak,
            ),
            behandlingStatus = "IVERKSATT",
            behandlingStatusBeskrivelse = "Behandlingen har blitt iverksatt.",
            resultat = "Opphør",
            resultatBeskrivelse = "En revurdering kan opphøre, mens en søknadsbehandling kan bli avslått.",
            resultatBegrunnelse = "UFØRHET",
            beslutter = "attestant",
            avsluttet = true,
        )
    }

    @Test
    fun `publiserer avsluttet revurdering`() {
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Avsluttet(
                revurdering = avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvsluttet"),
            ),
            behandlingStatus = "AVSLUTTET",
            behandlingStatusBeskrivelse = "Behandlingen har blitt avsluttet/lukket.",
            resultat = "Avbrutt",
            resultatBeskrivelse = "En paraplybetegnelse for å avbryte/avslutte/lukke en behandling der vi ikke har mer spesifikke data. Dekker bl.a.: [Bortfalt,Feilregistrert,Henlagt,Trukket,Avvist].",
            avsluttet = true,
            totrinnsbehandling = false,
            saksbehandler = "saksbehandlerSomAvsluttet",
        )
    }

    private fun assert(
        statistikkEvent: StatistikkEvent.Behandling.Revurdering,
        behandlingStatus: String,
        behandlingStatusBeskrivelse: String,
        resultat: String? = null,
        resultatBeskrivelse: String? = null,
        resultatBegrunnelse: String? = null,
        beslutter: String? = null,
        totrinnsbehandling: Boolean = true,
        avsluttet: Boolean = false,
        saksbehandler: String = "saksbehandler",
        behandlingYtelseDetaljer: String = """[
                         {
                           "satsgrunn":"BOR_ALENE"
                         }
                       ]""",
    ) {
        val kafkaPublisherMock: KafkaPublisher = mock()
        val personServiceMock: PersonService = mock()
        val sakRepoMock: SakRepo = mock()

        StatistikkEventObserverBuilder(
            kafkaPublisher = kafkaPublisherMock,
            personService = personServiceMock,
            sakRepo = sakRepoMock,
            clock = fixedClock,
            gitCommit = GitCommit("87a3a5155bf00b4d6854efcc24e8b929549c9302"),
        ).statistikkService.handle(statistikkEvent)

        verifyNoMoreInteractions(personServiceMock, sakRepoMock)

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe "supstonad.aapen-su-behandling-statistikk-v1" },
            argThat {
                // language=JSON
                JSONAssert.assertEquals(
                    """
                    {
                       "funksjonellTid":"2021-01-01T01:02:03.456789Z",
                       "tekniskTid":"2021-01-01T01:02:03.456789Z",
                       "mottattDato":"2021-01-01",
                       "registrertDato":"2021-01-01",
                       "behandlingId":${statistikkEvent.revurdering.id},
                       "sakId":${statistikkEvent.revurdering.sakId},
                       "saksnummer":"12345676",
                       "behandlingType":"REVURDERING",
                       "behandlingTypeBeskrivelse":"Revurdering av søknad for SU Uføre",
                       "behandlingStatus":"$behandlingStatus",
                       "behandlingStatusBeskrivelse":"$behandlingStatusBeskrivelse",
                       "behandlingYtelseDetaljer": $behandlingYtelseDetaljer,
                       "utenlandstilsnitt":"NASJONAL",
                       "ansvarligEnhetKode":"4815",
                       "ansvarligEnhetType":"NORG",
                       "behandlendeEnhetKode":"4815",
                       "avsender":"su-se-bakover",
                       "versjon":"87a3a5155bf00b4d6854efcc24e8b929549c9302",
                       "saksbehandler":"$saksbehandler",
                       ${if (resultat != null) """"resultat":"$resultat",""" else ""}
                       ${if (resultatBeskrivelse != null) """"resultatBeskrivelse":"$resultatBeskrivelse",""" else ""}
                       ${if (resultatBegrunnelse != null) """"resultatBegrunnelse":"$resultatBegrunnelse",""" else ""}
                       ${if (beslutter != null) """"beslutter":"$beslutter",""" else ""}
                       "avsluttet": $avsluttet,
                       "totrinnsbehandling": $totrinnsbehandling,
                       "behandlendeEnhetType":"NORG"
                    }
                    """.trimIndent(),
                    it,
                    true,
                )
            },
        )
        verifyNoMoreInteractions(kafkaPublisherMock, personServiceMock, sakRepoMock)
    }
}
