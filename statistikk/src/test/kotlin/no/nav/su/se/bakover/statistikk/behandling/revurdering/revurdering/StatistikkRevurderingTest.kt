package no.nav.su.se.bakover.statistikk.behandling.revurdering.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.revurderingUnderkjent
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert
import person.domain.PersonService
import java.time.ZoneOffset

internal class StatistikkRevurderingTest {

    @Test
    fun `publiserer opprettet revurdering`() {
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Opprettet(
                revurdering = opprettetRevurdering().second,
            ),
            behandlingStatus = "REGISTRERT",
            behandlingStatusBeskrivelse = "Vi har registrert en søknad, klage, revurdering, stans, gjenopptak eller lignende i systemet. Mottatt tidspunkt kan ha skjedd på et tidligere tidspunkt, som f.eks. ved papirsøknad og klage.",
            behandlingYtelseDetaljer = "[]",
            funksjonellTid = "2021-01-01T01:02:57.456789Z",
        )
    }

    @Test
    fun `publiserer innvilget revurdering til attestering`() {
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget(
                revurdering = revurderingTilAttestering().second as RevurderingTilAttestering.Innvilget,
            ),
            behandlingStatus = "TIL_ATTESTERING",
            behandlingStatusBeskrivelse = "Saksbehandler har sendt behandlingen videre til beslutter/attestant/saksbehandler2 som må velge og enten underkjenne(sendes tilbake til saksbehandler) eller iverksette (ferdigbehandler) den.",
            resultat = "INNVILGET",
            resultatBeskrivelse = "Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering.",
        )
    }

    @Test
    fun `publiserer opphørt revurdering til attestering`() {
        val revurdering =
            revurderingTilAttestering(
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            ).second as RevurderingTilAttestering.Opphørt
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.TilAttestering.Opphør(
                revurdering = revurdering,
            ),
            behandlingStatus = "TIL_ATTESTERING",
            behandlingStatusBeskrivelse = "Saksbehandler har sendt behandlingen videre til beslutter/attestant/saksbehandler2 som må velge og enten underkjenne(sendes tilbake til saksbehandler) eller iverksette (ferdigbehandler) den.",
            resultat = "OPPHØRT",
            resultatBeskrivelse = "En revurdering blir opphørt, mens en søknadsbehandling blir avslått.",
            resultatBegrunnelse = "UFØRHET",
        )
    }

    @Test
    fun `publiserer innvilget underkjent revurdering`() {
        val revurdering = revurderingUnderkjent().second as UnderkjentRevurdering.Innvilget
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Underkjent.Innvilget(
                revurdering = revurdering,
            ),
            behandlingStatus = "UNDERKJENT",
            behandlingStatusBeskrivelse = "beslutter/attestant/saksbehandler2 har sendt saken tilbake til saksbehandler.",
            resultat = "INNVILGET",
            resultatBeskrivelse = "Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering.",
            beslutter = "attestant",
            funksjonellTid = revurdering.prøvHentSisteAttestering()!!.opprettet.toString(),
        )
    }

    @Test
    fun `publiserer opphørt underkjent revurdering`() {
        val revurdering = revurderingUnderkjent(
            clock = tikkendeFixedClock(),
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).second as UnderkjentRevurdering.Opphørt
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Underkjent.Opphør(revurdering = revurdering),
            behandlingStatus = "UNDERKJENT",
            behandlingStatusBeskrivelse = "beslutter/attestant/saksbehandler2 har sendt saken tilbake til saksbehandler.",
            resultat = "OPPHØRT",
            resultatBeskrivelse = "En revurdering blir opphørt, mens en søknadsbehandling blir avslått.",
            resultatBegrunnelse = "UFØRHET",
            beslutter = "attestant",
            funksjonellTid = revurdering.prøvHentSisteAttestering()!!.opprettet.toString(),
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
            resultat = "INNVILGET",
            resultatBeskrivelse = "Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering.",
            beslutter = "attestant",
            avsluttet = true,
            funksjonellTid = vedtak.opprettet.toString(),
        )
    }

    @Test
    fun `publiserer iverksatt opphørt revurdering`() {
        val (_, vedtak) = vedtakRevurdering(
            clock = tikkendeFixedClock(),
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (sak, vedtak) -> sak to vedtak as VedtakOpphørMedUtbetaling }
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt(
                vedtak = vedtak,
            ),
            behandlingStatus = "IVERKSATT",
            behandlingStatusBeskrivelse = "Behandlingen har blitt iverksatt.",
            resultat = "OPPHØRT",
            resultatBeskrivelse = "En revurdering blir opphørt, mens en søknadsbehandling blir avslått.",
            resultatBegrunnelse = "UFØRHET",
            beslutter = "attestant",
            avsluttet = true,
            funksjonellTid = vedtak.opprettet.toString(),
        )
    }

    @Test
    fun `publiserer avsluttet revurdering`() {
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Revurdering.Avsluttet(
                revurdering = avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
                    tidspunktAvsluttet = 2.januar(2022).startOfDay(ZoneOffset.UTC),
                ).second,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvsluttet"),
            ),
            behandlingStatus = "AVSLUTTET",
            behandlingStatusBeskrivelse = "Behandlingen/søknaden har blitt avsluttet/lukket.",
            resultat = "AVBRUTT",
            resultatBeskrivelse = "En paraplybetegnelse for å avbryte/avslutte/lukke en behandling der vi ikke har mer spesifikke data. Spesifiseringer av Avbrutt: [FEILREGISTRERT, TRUKKET, AVVIST].",
            avsluttet = true,
            totrinnsbehandling = false,
            saksbehandler = "saksbehandlerSomAvsluttet",
            funksjonellTid = "2022-01-02T00:00:00Z",
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
        funksjonellTid: String = "2021-01-01T01:02:03.456789Z",
    ) {
        val kafkaPublisherMock: KafkaPublisher = mock()
        val personServiceMock: PersonService = mock()
        val sakRepoMock: SakRepo = mock()

        StatistikkEventObserverBuilder(
            kafkaPublisher = kafkaPublisherMock,
            personService = personServiceMock,
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
                       "funksjonellTid": "$funksjonellTid",
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
