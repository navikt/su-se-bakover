package no.nav.su.se.bakover.service.statistikk

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.nySakMedNySøknad
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class StatistikkServiceImplTest {
    private val sakTopicName = "supstonad.aapen-su-sak-statistikk-v1"
    private val behandlingTopicName = "supstonad.aapen-su-behandling-statistikk-v1"

    private val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)

    val stønadsperiode = år(2021)

    @Test
    fun `Gyldig sak publiserer till kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
        }

        StatistikkServiceImpl(
            publisher = kafkaPublisherMock,
            personService = mock(),
            sakRepo = mock(),
            vedtakRepo = mock(),
            clock = fixedClock,
        ).publiser(StatistikkSchemaValidatorTest.gyldigSak)
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe sakTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(StatistikkSchemaValidatorTest.gyldigSak) },
        )
    }

    @Test
    fun `Gyldig behandling publiserer till kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock()

        StatistikkServiceImpl(
            publisher = kafkaPublisherMock,
            personService = mock(),
            sakRepo = mock(),
            vedtakRepo = mock(),
            clock = fixedClock,
        ).publiser(StatistikkSchemaValidatorTest.gyldigBehandling)
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(StatistikkSchemaValidatorTest.gyldigBehandling) },
        )
    }

    @Test
    fun `publiserer SakOpprettet-event på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock()
        val personServiceMock: PersonService = mock {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("55").right()
        }
        val sak = nySakMedNySøknad().first

        val expected = Statistikk.Sak(
            funksjonellTid = sak.opprettet,
            tekniskTid = sak.opprettet,
            opprettetDato = sak.opprettet.toLocalDate(zoneIdOslo),
            sakId = sak.id,
            aktorId = 55,
            saksnummer = sak.saksnummer.nummer,
            sakStatus = "OPPRETTET",
            sakStatusBeskrivelse = "Sak er opprettet men ingen vedtak er fattet.",
            versjon = clock.millis(),
        )

        StatistikkServiceImpl(
            publisher = kafkaPublisherMock,
            personService = personServiceMock,
            sakRepo = mock(),
            vedtakRepo = mock(),
            clock = clock,
        ).handle(
            Event.Statistikk.SakOpprettet(sak),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe sakTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
        verify(personServiceMock).hentAktørIdMedSystembruker(
            argThat { it shouldBe sak.fnr },
        )
    }

    @Test
    fun `publiserer BehandlingOpprettet-event på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock()

        val søknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second

        val expected = Statistikk.Behandling(
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = søknadsbehandling.søknad.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = søknadsbehandling.søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknadsbehandling.id,
            sakId = søknadsbehandling.sakId,
            søknadId = søknadsbehandling.søknad.id,
            saksnummer = søknadsbehandling.saksnummer.nummer,
            behandlingStatus = søknadsbehandling.status.toString(),
            versjon = clock.millis(),
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = "Søknad for SU Uføre",
            behandlingStatusBeskrivelse = "Ny søknadsbehandling opprettet",
            utenlandstilsnitt = "NASJONAL",
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            avsender = "su-se-bakover",
            avsluttet = false,
        )

        StatistikkServiceImpl(
            publisher = kafkaPublisherMock,
            personService = mock(),
            sakRepo = mock(),
            vedtakRepo = mock(),
            clock = clock,
        ).handle(
            Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingOpprettet(søknadsbehandling),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer BehandlingTilAttestering-event på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock()

        val behandling = søknadsbehandlingTilAttesteringAvslagUtenBeregning().second

        val expected = Statistikk.Behandling(
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = behandling.søknad.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = behandling.søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = behandling.id,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer.nummer,
            behandlingStatus = behandling.status.toString(),
            behandlingStatusBeskrivelse = "Avslått søknadsbehanding sendt til attestering",
            søknadId = behandling.søknad.id,
            versjon = clock.millis(),
            saksbehandler = behandling.saksbehandler.navIdent,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            behandlingYtelseDetaljer = listOf(Statistikk.BehandlingYtelseDetaljer(Statistikk.Stønadsklassifisering.BOR_ALENE)),
            avsluttet = false,
        )

        StatistikkServiceImpl(
            publisher = kafkaPublisherMock,
            personService = mock(),
            sakRepo = mock(),
            vedtakRepo = mock(),
            clock = clock,
        ).handle(
            Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingTilAttestering(behandling),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer BehandlingIverksatt-event på kafka ved innvilgelse`() {
        val kafkaPublisherMock: KafkaPublisher = mock()

        val behandling = søknadsbehandlingIverksattInnvilget().second

        val expected = Statistikk.Behandling(
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = behandling.søknad.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = behandling.søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = behandling.id,
            sakId = behandling.sakId,
            søknadId = behandling.søknad.id,
            saksnummer = behandling.saksnummer.nummer,
            behandlingStatus = behandling.status.toString(),
            behandlingStatusBeskrivelse = "Innvilget søknadsbehandling iverksatt",
            versjon = clock.millis(),
            resultat = "Innvilget",
            saksbehandler = behandling.saksbehandler.navIdent,
            beslutter = behandling.attesteringer.hentSisteAttestering().attestant.navIdent,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            behandlingYtelseDetaljer = listOf(Statistikk.BehandlingYtelseDetaljer(Statistikk.Stønadsklassifisering.BOR_ALENE)),
            avsluttet = true,
        )

        StatistikkServiceImpl(
            publisher = kafkaPublisherMock,
            personService = mock(),
            sakRepo = mock(),
            vedtakRepo = mock(),
            clock = clock,
        ).handle(
            Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(behandling),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer BehandlingIverksatt-event på kafka ved avslag`() {
        val kafkaPublisherMock: KafkaPublisher = mock()

        val behandling = søknadsbehandlingIverksattAvslagUtenBeregning().second

        val expected = Statistikk.Behandling(
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = behandling.søknad.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = behandling.søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = behandling.id,
            sakId = behandling.sakId,
            søknadId = behandling.søknad.id,
            saksnummer = behandling.saksnummer.nummer,
            behandlingStatus = behandling.status.toString(),
            behandlingStatusBeskrivelse = "Avslått søknadsbehandling iverksatt",
            versjon = clock.millis(),
            resultat = "Avslått",
            saksbehandler = behandling.saksbehandler.navIdent,
            beslutter = behandling.attesteringer.hentSisteAttestering().attestant.navIdent,
            resultatBegrunnelse = "UFØRHET,FORMUE,FLYKTNING,OPPHOLDSTILLATELSE,BOR_OG_OPPHOLDER_SEG_I_NORGE,INNLAGT_PÅ_INSTITUSJON,UTENLANDSOPPHOLD_OVER_90_DAGER,PERSONLIG_OPPMØTE,MANGLENDE_DOKUMENTASJON",
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            behandlingYtelseDetaljer = listOf(Statistikk.BehandlingYtelseDetaljer(Statistikk.Stønadsklassifisering.BOR_ALENE)),
            avsluttet = true,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), mock(), mock(), clock).handle(
            Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(behandling),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for underkjent behandling på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock()
        val underkjent = søknadsbehandlingUnderkjentInnvilget().second

        val expected = Statistikk.Behandling(
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = underkjent.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = underkjent.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = underkjent.id,
            sakId = underkjent.sakId,
            søknadId = underkjent.søknad.id,
            saksnummer = underkjent.saksnummer.nummer,
            behandlingStatus = underkjent.status.toString(),
            behandlingStatusBeskrivelse = "Innvilget søknadsbehandling sendt tilbake fra attestant til saksbehandler",
            versjon = clock.millis(),
            saksbehandler = underkjent.saksbehandler.navIdent,
            beslutter = underkjent.attesteringer.hentSisteAttestering().attestant.navIdent,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            behandlingYtelseDetaljer = listOf(Statistikk.BehandlingYtelseDetaljer(Statistikk.Stønadsklassifisering.BOR_ALENE)),
            avsluttet = false,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), mock(), mock(), clock).handle(
            Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingUnderkjent(underkjent),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for opprettet revurdering på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock()
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        val expected = Statistikk.Behandling(
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = opprettetRevurdering.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = opprettetRevurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = opprettetRevurdering.id,
            sakId = opprettetRevurdering.sakId,
            saksnummer = opprettetRevurdering.saksnummer.nummer,
            behandlingStatus = "OPPRETTET",
            behandlingStatusBeskrivelse = "Ny revurdering opprettet",
            versjon = clock.millis(),
            saksbehandler = opprettetRevurdering.saksbehandler.navIdent,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            relatertBehandlingId = opprettetRevurdering.tilRevurdering,
            avsluttet = false,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), mock(), mock(), clock).handle(
            Event.Statistikk.RevurderingStatistikk.RevurderingOpprettet(opprettetRevurdering),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for revurdering sendt til attestering på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock()
        val revurderingTilAttestering = tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        val expected = Statistikk.Behandling(
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = revurderingTilAttestering.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = revurderingTilAttestering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = revurderingTilAttestering.id,
            sakId = revurderingTilAttestering.sakId,
            saksnummer = revurderingTilAttestering.saksnummer.nummer,
            behandlingStatus = "TIL_ATTESTERING_INNVILGET",
            behandlingStatusBeskrivelse = "Innvilget revurdering sendt til attestering",
            versjon = clock.millis(),
            saksbehandler = revurderingTilAttestering.saksbehandler.navIdent,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            relatertBehandlingId = revurderingTilAttestering.tilRevurdering,
            avsluttet = false,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), mock(), mock(), clock).handle(
            Event.Statistikk.RevurderingStatistikk.RevurderingTilAttestering(revurderingTilAttestering),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for iverksetting av revurdering på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock()
        val iverksattRevurdering = iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        val expected = Statistikk.Behandling(
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = iverksattRevurdering.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = iverksattRevurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = iverksattRevurdering.id,
            sakId = iverksattRevurdering.sakId,
            saksnummer = iverksattRevurdering.saksnummer.nummer,
            behandlingStatus = "IVERKSATT_INNVILGET",
            behandlingStatusBeskrivelse = "Innvilget revurdering iverksatt",
            behandlingYtelseDetaljer = listOf(Statistikk.BehandlingYtelseDetaljer(Statistikk.Stønadsklassifisering.BOR_ALENE)),
            versjon = clock.millis(),
            saksbehandler = iverksattRevurdering.saksbehandler.navIdent,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            relatertBehandlingId = iverksattRevurdering.tilRevurdering,
            resultat = "Innvilget",
            resultatBegrunnelse = null,
            beslutter = iverksattRevurdering.attestering.attestant.navIdent,
            avsluttet = true,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), mock(), mock(), clock).handle(
            Event.Statistikk.RevurderingStatistikk.RevurderingIverksatt(iverksattRevurdering),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for mottat søknad på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock()
        val saksnummer = Saksnummer(2049L)
        val søknad = nySakMedNySøknad().second

        val expected = Statistikk.Behandling(
            funksjonellTid = søknad.opprettet,
            tekniskTid = søknad.opprettet,
            registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknad.id,
            sakId = søknad.sakId,
            saksnummer = saksnummer.nummer,
            behandlingStatus = Statistikk.Behandling.SøknadStatus.SØKNAD_MOTTATT.name,
            behandlingStatusBeskrivelse = Statistikk.Behandling.SøknadStatus.SØKNAD_MOTTATT.beskrivelse,
            versjon = clock.millis(),
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            relatertBehandlingId = null,
            totrinnsbehandling = false,
            avsluttet = false,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), mock(), mock(), clock).handle(
            Event.Statistikk.SøknadStatistikk.SøknadMottatt(søknad, saksnummer),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for lukket søknad på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock()
        val saksnummer = Saksnummer(2049L)
        val søknad = Søknad.Journalført.MedOppgave.Lukket(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            innsendtAv = veileder,
            journalpostId = JournalpostId("journalpostid"),
            oppgaveId = OppgaveId("oppgaveid"),
            lukketTidspunkt = fixedTidspunkt,
            lukketAv = NavIdentBruker.Saksbehandler("Mr Lukker"),
            lukketType = Søknad.Journalført.MedOppgave.Lukket.LukketType.AVVIST,
        )

        val expected = Statistikk.Behandling(
            funksjonellTid = søknad.lukketTidspunkt,
            tekniskTid = søknad.lukketTidspunkt,
            registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknad.id,
            sakId = søknad.sakId,
            saksnummer = saksnummer.nummer,
            behandlingStatus = Statistikk.Behandling.SøknadStatus.SØKNAD_LUKKET.name,
            behandlingStatusBeskrivelse = Statistikk.Behandling.SøknadStatus.SØKNAD_LUKKET.beskrivelse,
            versjon = clock.millis(),
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            relatertBehandlingId = null,
            totrinnsbehandling = false,
            avsluttet = true,
            resultat = "AVVIST",
            saksbehandler = søknad.lukketAv.toString(),
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), mock(), mock(), clock).handle(
            Event.Statistikk.SøknadStatistikk.SøknadLukket(søknad, saksnummer),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }
}
