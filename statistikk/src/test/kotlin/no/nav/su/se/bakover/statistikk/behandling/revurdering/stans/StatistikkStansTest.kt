package no.nav.su.se.bakover.statistikk.behandling.revurdering.stans

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert

internal class StatistikkStansTest {

    @Test
    fun `simulert stans`() {
        val stans = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Stans.Opprettet(stans),
            behandlingStatus = "REGISTRERT",
            behandlingStatusBeskrivelse = "Vi har registrert en søknad, klage, revurdering, stans, gjenopptak eller lignende i systemet. Mottatt tidspunkt kan ha skjedd på et tidligere tidspunkt, som f.eks. ved papirsøknad og klage.",
            resultat = "STANSET",
            resultatBeskrivelse = "Stønadsendring som fører til stans av utbetalingen(e) for gitt(e) måned(er). Det motsatte av resultatet [GJENOPPTATT].",
            resultatBegrunnelse = "MANGLENDE_KONTROLLERKLÆRING",
            funksjonellTid = stans.opprettet,
        )
    }

    @Test
    fun `iverksatt stans`() {
        val vedtak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Stans.Iverksatt(vedtak),
            behandlingStatus = "IVERKSATT",
            behandlingStatusBeskrivelse = "Behandlingen har blitt iverksatt.",
            resultat = "STANSET",
            resultatBeskrivelse = "Stønadsendring som fører til stans av utbetalingen(e) for gitt(e) måned(er). Det motsatte av resultatet [GJENOPPTATT].",
            resultatBegrunnelse = "MANGLENDE_KONTROLLERKLÆRING",
            avsluttet = true,
            funksjonellTid = vedtak.opprettet,
        )
    }

    @Test
    fun `avsluttet gjenopptak`() {
        val stans = avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Stans.Avsluttet(stans),
            behandlingStatus = "AVSLUTTET",
            behandlingStatusBeskrivelse = "Behandlingen/søknaden har blitt avsluttet/lukket.",
            resultat = "AVBRUTT",
            resultatBeskrivelse = "En paraplybetegnelse for å avbryte/avslutte/lukke en behandling der vi ikke har mer spesifikke data. Spesifiseringer av Avbrutt: [FEILREGISTRERT, TRUKKET, AVVIST].",
            avsluttet = true,
            funksjonellTid = stans.avsluttetTidspunkt,
        )
    }

    private fun assert(
        statistikkEvent: StatistikkEvent.Behandling.Stans,
        behandlingStatus: String,
        behandlingStatusBeskrivelse: String,
        resultat: String? = null,
        resultatBeskrivelse: String? = null,
        resultatBegrunnelse: String? = null,
        avsluttet: Boolean = false,
        saksbehandler: String = "saksbehandler",
        behandlingYtelseDetaljer: String = """[
                         {
                           "satsgrunn":"BOR_ALENE"
                         }
                       ]""",
        funksjonellTid: Tidspunkt,
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
                       "funksjonellTid":"$funksjonellTid",
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
                       "avsluttet": $avsluttet,
                       "totrinnsbehandling": false,
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
