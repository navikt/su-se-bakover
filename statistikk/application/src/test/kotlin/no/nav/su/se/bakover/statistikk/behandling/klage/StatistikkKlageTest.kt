package no.nav.su.se.bakover.statistikk.behandling.klage

import behandling.klage.domain.Hjemmel
import behandling.klage.domain.Klagehjemler
import behandling.klage.domain.VurderingerTilKlage
import behandling.klage.domain.VurderingerTilKlage.Vedtaksvurdering.Årsak
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avsluttetKlage
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID

internal class StatistikkKlageTest {

    @Test
    fun `opprettet klage`() {
        val klage = opprettetKlage().second

        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.Opprettet(klage, UUID.randomUUID()),
            behandlingStatus = BehandlingStatus.Registrert.value,
            behandlingStatusBeskrivelse = "Vi har registrert en søknad, klage, revurdering, stans, gjenopptak eller lignende i systemet. Mottatt tidspunkt kan ha skjedd på et tidligere tidspunkt, som f.eks. ved papirsøknad og klage.",
            funksjonellTid = klage.opprettet,
        )
    }

    @Test
    fun `avsluttet klage`() {
        val klage = avsluttetKlage().second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.Avsluttet(klage),
            behandlingStatus = BehandlingStatus.Avsluttet.value,
            behandlingStatusBeskrivelse = "Behandlingen/søknaden har blitt avsluttet/lukket.",
            resultat = BehandlingResultat.Avbrutt.value,
            resultatBeskrivelse = "En paraplybetegnelse for å avbryte/avslutte/lukke en behandling der vi ikke har mer spesifikke data. Spesifiseringer av Avbrutt: [FEILREGISTRERT, TRUKKET, AVVIST].",
            avsluttet = true,
            totrinnsbehandling = false,
            funksjonellTid = fixedTidspunkt,
        )
    }

    @Test
    fun `oversendt klage til ka med oppretthold`() {
        val vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createDelvisEllerOpprettholdelse(
            hjemler = Klagehjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).getOrFail(),
            klagenotat = "klagenotat",
            erOppretthold = true,
        ).getOrFail()
        val klage = oversendtKlage(vedtaksvurdering = vedtaksvurdering).second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.Oversendt(klage),
            behandlingStatus = BehandlingStatus.OversendtKlage.value,
            behandlingStatusBeskrivelse = "Oversendt innstilling til klageinstansen. Denne er unik for klage. Brukes f.eks. ved resultatet [OPPRETTHOLDT].",
            resultat = BehandlingResultat.OpprettholdtKlage.value,
            resultatBeskrivelse = "Kun brukt i klagebehandling ved oversendelse til klageinstansen.",
            resultatBegrunnelse = "SU_PARAGRAF_3,SU_PARAGRAF_4",
            beslutter = "attestant",
            funksjonellTid = Tidspunkt.create(Instant.parse("2021-02-01T01:02:04.456789Z")),
        )
    }

    @Test
    fun `oversendt klage delvis omgjøring`() {
        val vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createDelvisEllerOpprettholdelse(
            hjemler = Klagehjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).getOrFail(),
            klagenotat = "klagenotat",
            erOppretthold = false,
        ).getOrFail()
        val klage = oversendtKlage(vedtaksvurdering = vedtaksvurdering).second
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.Oversendt(klage),
            behandlingStatus = BehandlingStatus.OversendtKlage.value,
            behandlingStatusBeskrivelse = "Oversendt innstilling til klageinstansen. Denne er unik for klage. Brukes f.eks. ved resultatet [OPPRETTHOLDT].",
            resultat = BehandlingResultat.DelvisOmgjøringKa.value,
            resultatBeskrivelse = "Kun brukt i klagebehandling ved oversendelse til klageinstansen.",
            resultatBegrunnelse = "SU_PARAGRAF_3,SU_PARAGRAF_4",
            beslutter = "attestant",
            funksjonellTid = Tidspunkt.create(Instant.parse("2021-02-01T01:02:04.456789Z")),
        )
    }

    @Test
    fun `klage omgjøring egen vedtaksinstans`() {
        val vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOmgjør(
            årsak = Årsak.FEIL_LOVANVENDELSE,
            begrunnelse = "test",
            erDelvisOmgjøring = false,
        )
        val sakstype = Sakstype.UFØRE
        val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
        val klage = VurdertKlage.Bekreftet.createBekreftet(
            forrigeSteg = utfyltVurdertKlage(
                vedtaksvurdering = vedtaksvurdering,
            ).second,
            saksbehandler = saksbehandler,
            sakstype = sakstype,
        )
        val bekreftetKlageVedtaksinstans = when (klage) {
            is VurdertKlage.BekreftetBehandlesIVedtaksinstans -> klage
            is VurdertKlage.BekreftetTilOversending -> throw IllegalStateException("feil testdata")
        }

        val ferdigstiltOmgjøring = bekreftetKlageVedtaksinstans.ferdigstillOmgjøring(saksbehandler, Tidspunkt.now(fixedClock)).getOrFail()
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.FerdigstiltOmgjøring(ferdigstiltOmgjøring),
            behandlingStatus = BehandlingStatus.Iverksatt.value,
            resultatBeskrivelse = BehandlingResultat.OmgjortKlage.beskrivelse,
            behandlingStatusBeskrivelse = BehandlingStatus.Iverksatt.beskrivelse,
            resultat = BehandlingResultat.OmgjortKlage.value,
            resultatBegrunnelse = ferdigstiltOmgjøring.vurderinger.vedtaksvurdering.årsak.name.uppercase(),
            beslutter = null,
            funksjonellTid = Tidspunkt.create(Instant.parse("2021-01-01T01:02:03.456789Z")),
            avsluttet = true,
            totrinnsbehandling = false,
        )
    }

    @Test
    fun `klage delvis omgjøring egen vedtaksinstans`() {
        val vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOmgjør(
            årsak = Årsak.FEIL_LOVANVENDELSE,
            begrunnelse = "test",
            erDelvisOmgjøring = true,
        )
        val sakstype = Sakstype.UFØRE
        val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
        val klage = VurdertKlage.Bekreftet.createBekreftet(
            forrigeSteg = utfyltVurdertKlage(
                vedtaksvurdering = vedtaksvurdering,
            ).second,
            saksbehandler = saksbehandler,
            sakstype = sakstype,
        )
        val bekreftetKlageVedtaksinstans = when (klage) {
            is VurdertKlage.BekreftetBehandlesIVedtaksinstans -> klage
            is VurdertKlage.BekreftetTilOversending -> throw IllegalStateException("feil testdata")
        }

        val ferdigstiltOmgjøring = bekreftetKlageVedtaksinstans.ferdigstillOmgjøring(saksbehandler, Tidspunkt.now(fixedClock)).getOrFail()
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.FerdigstiltOmgjøring(ferdigstiltOmgjøring),
            behandlingStatus = BehandlingStatus.Iverksatt.value,
            resultatBeskrivelse = BehandlingResultat.DelvisOmgjøringEgenVedtaksinstans.beskrivelse,
            behandlingStatusBeskrivelse = BehandlingStatus.Iverksatt.beskrivelse,
            resultat = BehandlingResultat.DelvisOmgjøringEgenVedtaksinstans.value,
            resultatBegrunnelse = ferdigstiltOmgjøring.vurderinger.vedtaksvurdering.årsak.name.uppercase(),
            beslutter = null,
            funksjonellTid = Tidspunkt.create(Instant.parse("2021-01-01T01:02:03.456789Z")),
            avsluttet = true,
            totrinnsbehandling = false,
        )
    }

    @Test
    fun `iverksatt avvist klage`() {
        val (sak, _) = iverksattAvvistKlage()

        val vedtak = sak.vedtakListe[1] as Klagevedtak.Avvist
        assert(
            statistikkEvent = StatistikkEvent.Behandling.Klage.Avvist(vedtak),
            behandlingStatus = BehandlingStatus.Iverksatt.value,
            behandlingStatusBeskrivelse = "Behandlingen har blitt iverksatt.",
            resultat = BehandlingResultat.Avslag.value,
            resultatBeskrivelse = "Søknaden blir lukket med status avslag.",
            resultatBegrunnelse = "IKKE_INNENFOR_FRISTEN",
            beslutter = "attestant",
            avsluttet = true,
            funksjonellTid = fixedTidspunkt,
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
            clock = fixedClock,
            gitCommit = GitCommit("87a3a5155bf00b4d6854efcc24e8b929549c9302"),
        ).statistikkService.handle(statistikkEvent)
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe "supstonad.aapen-su-behandling-statistikk-v1" },
            argThat { json ->
                val expected = mutableMapOf<String, Any?>(
                    "funksjonellTid" to funksjonellTid.toString(),
                    "tekniskTid" to "2021-01-01T01:02:03.456789Z",
                    "mottattDato" to statistikkEvent.klage.datoKlageMottatt.toString(),
                    "registrertDato" to "2021-02-01",
                    "behandlingId" to statistikkEvent.klage.id.toString(),
                    "sakId" to statistikkEvent.klage.sakId.toString(),
                    "saksnummer" to "12345676",
                    "ytelseType" to "SUUFORE",
                    "behandlingType" to "KLAGE",
                    "behandlingTypeBeskrivelse" to "Klage for SU Uføre",
                    "behandlingStatus" to behandlingStatus,
                    "behandlingStatusBeskrivelse" to behandlingStatusBeskrivelse,
//                    "behandlingYtelseDetaljer" to jacksonObjectMapper().readValue(behandlingYtelseDetaljer, object : TypeReference<List<Map<String, Any?>>>() {}),
                    "utenlandstilsnitt" to "NASJONAL",
                    "ansvarligEnhetKode" to "4815",
                    "ansvarligEnhetType" to "NORG",
                    "behandlendeEnhetKode" to "4815",
                    "behandlendeEnhetType" to "NORG",
                    "avsender" to "su-se-bakover",
                    "saksbehandler" to saksbehandler,
                    "avsluttet" to avsluttet,
                    "totrinnsbehandling" to totrinnsbehandling,
                    "versjon" to "87a3a5155bf00b4d6854efcc24e8b929549c9302",
                )
                if (resultat != null) expected["resultat"] = resultat
                if (resultatBeskrivelse != null) expected["resultatBeskrivelse"] = resultatBeskrivelse
                if (resultatBegrunnelse != null) expected["resultatBegrunnelse"] = resultatBegrunnelse
                if (beslutter != null) expected["beslutter"] = beslutter
                val vedtakId = statistikkEvent.klage.vilkårsvurderinger?.vedtakId
                if (vedtakId != null) expected["relatertBehandlingId"] = vedtakId.toString()

                val actual = jacksonObjectMapper().readValue(json, object : TypeReference<Map<String, Any?>>() {})

                val mismatch = expected.mapNotNull { (key, value) ->
                    val a = actual[key]
                    if (a != value) "$key: expected=$value actual=$a" else null
                }

                if (mismatch.isNotEmpty()) {
                    error("mismatch:\n${mismatch.joinToString("\n")}")
                }
            },
        )
    }
}
