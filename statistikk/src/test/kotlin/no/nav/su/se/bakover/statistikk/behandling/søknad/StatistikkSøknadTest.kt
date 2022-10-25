package no.nav.su.se.bakover.statistikk.behandling.søknad

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.søknad.avvistSøknadUtenBrev
import no.nav.su.se.bakover.test.søknad.bortfaltSøknad
import no.nav.su.se.bakover.test.søknad.nySakMedNySøknad
import no.nav.su.se.bakover.test.søknad.trukketSøknad
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

internal class StatistikkSøknadTest {

    @Test
    fun `ny søknad`() {
        val (sak, søknad) = nySakMedNySøknad()
        assert(
            statistikkEvent = StatistikkEvent.Søknad.Mottatt(søknad, sak.saksnummer),
            behandlingStatus = "REGISTRERT",
            behandlingStatusBeskrivelse = "Vi har registrert en søknad, klage, revurdering, stans, gjenopptak eller lignende i systemet. Mottatt tidspunkt kan ha skjedd på et tidligere tidspunkt, som f.eks. ved papirsøknad og klage.",
            funksjonellTid = søknad.opprettet,
            saksbehandler = "veileder",
        )
    }

    @Test
    fun `feilregistrert søknad`() {
        val (sak, søknad) = bortfaltSøknad()
        assert(
            statistikkEvent = StatistikkEvent.Søknad.Lukket(søknad, sak.saksnummer),
            behandlingStatus = "AVSLUTTET",
            behandlingStatusBeskrivelse = "Behandlingen/søknaden har blitt avsluttet/lukket.",
            resultat = "FEILREGISTRERT",
            resultatBeskrivelse = "Feilaktig registrert behandling. En spesifisering av [AVBRUTT]",
            funksjonellTid = søknad.lukketTidspunkt,
            avsluttet = true,
            totrinnsbehandling = false,
        )
    }

    @Test
    fun `avvist søknad`() {
        val (sak, søknad) = avvistSøknadUtenBrev()
        assert(
            statistikkEvent = StatistikkEvent.Søknad.Lukket(søknad, sak.saksnummer),
            behandlingStatus = "AVSLUTTET",
            behandlingStatusBeskrivelse = "Behandlingen/søknaden har blitt avsluttet/lukket.",
            resultat = "AVVIST",
            resultatBeskrivelse = "Avvist pga. bl.a. formkrav. En spesifisering av [AVBRUTT].",
            funksjonellTid = søknad.lukketTidspunkt,
            avsluttet = true,
            totrinnsbehandling = false,
        )
    }

    @Test
    fun `trukket søknad`() {
        val (sak, søknad) = trukketSøknad()
        assert(
            statistikkEvent = StatistikkEvent.Søknad.Lukket(søknad, sak.saksnummer),
            behandlingStatus = "AVSLUTTET",
            behandlingStatusBeskrivelse = "Behandlingen/søknaden har blitt avsluttet/lukket.",
            resultat = "TRUKKET",
            resultatBeskrivelse = "Bruker eller verge/fullmakt har bedt om å trekke søknad/klage. En spesifisering av [AVBRUTT].",
            funksjonellTid = søknad.lukketTidspunkt,
            avsluttet = true,
            totrinnsbehandling = false,
        )
    }

    private fun assert(
        statistikkEvent: StatistikkEvent.Søknad,
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
        behandlingYtelseDetaljer: String = "[]",
    ) {
        val kafkaPublisherMock: KafkaPublisher = mock()

        StatistikkEventObserverBuilder(
            kafkaPublisher = kafkaPublisherMock,
            personService = mock(),
            sakRepo = mock(),
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
                       "mottattDato":${statistikkEvent.søknad.mottaksdato},
                       "registrertDato":"2021-01-01",
                       "sakId":${statistikkEvent.søknad.sakId},
                       "søknadId":${statistikkEvent.søknad.id},
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
