package no.nav.su.se.bakover.statistikk.behandling.klage

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.statistikk.behandling.toFunksjonellTid
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avsluttetKlage
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

internal class StatistikkKlageTest {

    private val gitCommit = GitCommit("87a3a5155bf00b4d6854efcc24e8b929549c9302")

    @Test
    fun `opprettet klage`() {
        val klage = opprettetKlage().second

        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.Opprettet(klage),
            behandlingStatus = "REGISTRERT",
            behandlingStatusBeskrivelse = "Opprettet/registrert behandling. Dette er ofte trigget av en saksbehandler.",
            funksjonellTid = klage.opprettet,
        )
    }

    @Test
    fun `avsluttet klage`() {
        val klage = avsluttetKlage().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.Avsluttet(klage),
            behandlingStatus = "AVSLUTTET",
            behandlingStatusBeskrivelse = "Behandlingen har blitt avsluttet/lukket.",
            resultat = "Avbrutt",
            resultatBeskrivelse = "En paraplybetegnelse for å avbryte/avslutte/lukke en behandling der vi ikke har mer spesifikke data. Dekker bl.a.: [Bortfalt,Feilregistrert,Henlagt,Trukket,Avvist].",
            avsluttet = true,
            totrinnsbehandling = false,
            funksjonellTid = klage.attesteringer.toFunksjonellTid(klage.id, fixedClock),
        )
    }

    @Test
    fun `oversendt klage`() {
        val klage = oversendtKlage().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.Oversendt(klage),
            behandlingStatus = "OVERSENDT",
            behandlingStatusBeskrivelse = "Oversendt til klageinstansen. Denne er unik for klage. Brukes f.eks. ved resultatet [Opprettholdt].",
            resultat = "Opprettholdt",
            resultatBeskrivelse = "Kun brukt i klagebehandling ved oversendelse til klageinstansen.",
            resultatBegrunnelse = "SU_PARAGRAF_3,SU_PARAGRAF_4",
            beslutter = "attestant",
            funksjonellTid = klage.attesteringer.toFunksjonellTid(klage.id, fixedClock),
        )
    }

    @Test
    fun `iverksatt avvist klage`() {
        val (sak, _) = iverksattAvvistKlage()

        val vedtak = sak.vedtakListe[1] as Klagevedtak.Avvist
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.Avvist(vedtak),
            behandlingStatus = "IVERKSATT",
            behandlingStatusBeskrivelse = "Behandlingen har blitt iverksatt.",
            resultat = "Avvist",
            resultatBeskrivelse = "Spesifisering av [Avbrutt]. F.eks. formkrav.",
            resultatBegrunnelse = "IKKE_INNENFOR_FRISTEN",
            beslutter = "attestant",
            avsluttet = true,
            funksjonellTid = vedtak.klage.attesteringer.toFunksjonellTid(vedtak.klage.id, fixedClock),
        )
    }

    private fun assert(
        statistikkEvent: StatistikkEvent.Behandling.Klage,
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
                       "mottattDato":${statistikkEvent.klage.datoKlageMottatt},
                       "registrertDato":"2021-01-01",
                       "behandlingId":${statistikkEvent.klage.id},
                       "sakId":${statistikkEvent.klage.sakId},
                       "saksnummer":"12345676",
                       "behandlingType":"KLAGE",
                       "behandlingTypeBeskrivelse":"Klage for SU Uføre",
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
                       ${
                    when (val v = statistikkEvent.klage.vilkårsvurderinger?.vedtakId) {
                        null -> ""
                        else -> """"relatertBehandlingId":"$v","""
                    }
                    }
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
