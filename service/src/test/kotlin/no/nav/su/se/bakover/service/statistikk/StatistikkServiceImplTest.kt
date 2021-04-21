package no.nav.su.se.bakover.service.statistikk

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class StatistikkServiceImplTest {
    private val sakTopicName = "supstonad.aapen-su-sak-statistikk-v1"
    private val behandlingTopicName = "supstonad.aapen-su-behandling-statistikk-v1"
    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )

    @Test
    fun `Gyldig sak publiserer till kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }

        StatistikkServiceImpl(kafkaPublisherMock, mock(), fixedClock).publiser(StatistikkSchemaValidatorTest.gyldigSak)
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe sakTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(StatistikkSchemaValidatorTest.gyldigSak) },
        )
    }

    @Test
    fun `Gyldig behandling publiserer till kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }

        StatistikkServiceImpl(
            kafkaPublisherMock,
            mock(),
            fixedClock,
        ).publiser(StatistikkSchemaValidatorTest.gyldigBehandling)
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(StatistikkSchemaValidatorTest.gyldigBehandling) },
        )
    }

    @Test
    fun `publiserer SakOpprettet-event på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn AktørId("55").right()
        }

        val sak = Sak(
            id = UUID.randomUUID(),
            saksnummer = Saksnummer(nummer = 2021),
            opprettet = Tidspunkt.now(fixedClock),
            fnr = FnrGenerator.random(),
            søknader = listOf(),
            behandlinger = listOf(),
            utbetalinger = listOf(),
        )
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
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

        StatistikkServiceImpl(kafkaPublisherMock, personServiceMock, clock).handle(
            Event.Statistikk.SakOpprettet(sak),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe sakTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
        verify(personServiceMock).hentAktørId(
            argThat { it shouldBe sak.fnr },
        )
    }

    @Test
    fun `publiserer BehandlingOpprettet-event på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val søknadMock: Søknad.Journalført.MedOppgave =
            mock { on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build() }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)

        val søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart = mock {
            on { opprettet } doReturn Tidspunkt.now()
            on { søknad } doReturn søknadMock
            on { opprettet } doReturn Tidspunkt.now()
            on { id } doReturn UUID.randomUUID()
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(5959)
            on { status } doReturn BehandlingsStatus.OPPRETTET
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
        }

        val expected = Statistikk.Behandling(
            funksjonellTid = søknadsbehandling.periode.fraOgMed.startOfDay(zoneIdOslo),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = søknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = søknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknadsbehandling.id,
            sakId = søknadsbehandling.sakId,
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

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingOpprettet(søknadsbehandling),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer BehandlingTilAttestering-event på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val søknadMock: Søknad.Journalført.MedOppgave =
            mock { on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build() }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)

        val behandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning = mock {
            on { opprettet } doReturn Tidspunkt.now()
            on { søknad } doReturn søknadMock
            on { opprettet } doReturn Tidspunkt.now()
            on { id } doReturn UUID.randomUUID()
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(5959)
            on { status } doReturn BehandlingsStatus.IVERKSATT_AVSLAG
            on { saksbehandler } doReturn NavIdentBruker.Saksbehandler("Z1595")
            on { avslagsgrunner } doReturn listOf(Avslagsgrunn.UFØRHET, Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER)
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
        }

        val expected = Statistikk.Behandling(
            funksjonellTid = behandling.periode.fraOgMed.startOfDay(zoneIdOslo),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = behandling.id,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer.nummer,
            behandlingStatus = behandling.status.toString(),
            behandlingStatusBeskrivelse = "Avslått søknadsbehandling iverksatt",
            versjon = clock.millis(),
            saksbehandler = "Z1595",
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            avsluttet = false,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingTilAttestering(behandling),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer BehandlingIverksatt-event på kafka ved innvilgelse`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val søknadMock: Søknad.Journalført.MedOppgave =
            mock { on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build() }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
        val beregningMock: Beregning = mock {
            on { getPeriode() } doReturn Periode.create(1.januar(2021), 31.januar(2021))
        }

        val behandling: Søknadsbehandling.Iverksatt.Innvilget = mock {
            on { opprettet } doReturn Tidspunkt.now()
            on { søknad } doReturn søknadMock
            on { opprettet } doReturn Tidspunkt.now()
            on { id } doReturn UUID.randomUUID()
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(5959)
            on { status } doReturn BehandlingsStatus.IVERKSATT_INNVILGET
            on { beregning } doReturn beregningMock
            on { saksbehandler } doReturn NavIdentBruker.Saksbehandler("55")
            on { attestering } doReturn Attestering.Iverksatt(NavIdentBruker.Attestant("56"))
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
        }

        val expected = Statistikk.Behandling(
            funksjonellTid = behandling.periode.fraOgMed.startOfDay(zoneIdOslo),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = behandling.id,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer.nummer,
            behandlingStatus = behandling.status.toString(),
            behandlingStatusBeskrivelse = "Innvilget søknadsbehandling iverksatt",
            versjon = clock.millis(),
            resultat = "Innvilget",
            saksbehandler = "55",
            beslutter = "56",
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            avsluttet = true,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(behandling),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer BehandlingIverksatt-event på kafka ved avslag`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val søknadMock: Søknad.Journalført.MedOppgave =
            mock { on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build() }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)

        val behandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning = mock {
            on { opprettet } doReturn Tidspunkt.now()
            on { søknad } doReturn søknadMock
            on { opprettet } doReturn Tidspunkt.now()
            on { id } doReturn UUID.randomUUID()
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(5959)
            on { status } doReturn BehandlingsStatus.IVERKSATT_AVSLAG
            on { saksbehandler } doReturn NavIdentBruker.Saksbehandler("55")
            on { attestering } doReturn Attestering.Iverksatt(NavIdentBruker.Attestant("56"))
            on { avslagsgrunner } doReturn listOf(Avslagsgrunn.UFØRHET, Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER)
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
        }

        val expected = Statistikk.Behandling(
            funksjonellTid = behandling.periode.fraOgMed.startOfDay(zoneIdOslo),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = behandling.id,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer.nummer,
            behandlingStatus = behandling.status.toString(),
            behandlingStatusBeskrivelse = "Avslått søknadsbehandling iverksatt",
            versjon = clock.millis(),
            resultat = "Avslått",
            saksbehandler = "55",
            beslutter = "56",
            resultatBegrunnelse = "UFØRHET,UTENLANDSOPPHOLD_OVER_90_DAGER",
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            avsluttet = true,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(behandling),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for underkjent behandling på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val søknadMock: Søknad.Journalført.MedOppgave =
            mock { on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build() }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)

        val beregning = TestBeregning

        val underkjent = Søknadsbehandling.Underkjent.Innvilget(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(0),
            søknad = søknadMock,
            oppgaveId = OppgaveId(value = ""),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            fnr = FnrGenerator.random(),
            beregning = beregning,
            simulering = mock(),
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            attestering = Attestering.Underkjent(
                NavIdentBruker.Attestant("attestant"),
                Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                "",
            ),
            fritekstTilBrev = "",
            stønadsperiode = ValgtStønadsperiode(Periode.create(1.januar(2021), 31.desember(2021))),
        )

        val expected = Statistikk.Behandling(
            funksjonellTid = underkjent.periode.fraOgMed.startOfDay(zoneIdOslo),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = underkjent.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = underkjent.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = underkjent.id,
            sakId = underkjent.sakId,
            saksnummer = underkjent.saksnummer.nummer,
            behandlingStatus = underkjent.status.toString(),
            behandlingStatusBeskrivelse = "Innvilget søknadsbehandling sendt tilbake fra attestant til saksbehandler",
            versjon = clock.millis(),
            saksbehandler = "saksbehandler",
            beslutter = "attestant",
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            avsluttet = false,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingUnderkjent(underkjent),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for opprettet revurdering på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
        val behandlingsperiode = Periode.create(1.januar(2021), 31.desember(2021))
        val behandlingMock = mock<Behandling> {
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(49L)
        }
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            tilRevurdering = mock {
                on { behandling } doReturn behandlingMock
                on { id } doReturn UUID.randomUUID()
            },
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            periode = behandlingsperiode,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = RevurderingTestUtils.søknadsbehandlingVedtak.behandlingsinformasjon,
        )

        val expected = Statistikk.Behandling(
            funksjonellTid = opprettetRevurdering.opprettet,
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = opprettetRevurdering.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = opprettetRevurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = opprettetRevurdering.id,
            sakId = opprettetRevurdering.sakId,
            saksnummer = opprettetRevurdering.saksnummer.nummer,
            behandlingStatus = "OPPRETTET",
            behandlingStatusBeskrivelse = "Ny revurdering opprettet",
            versjon = clock.millis(),
            saksbehandler = "saksbehandler",
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            relatertBehandlingId = opprettetRevurdering.tilRevurdering.id,
            avsluttet = false,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.RevurderingStatistikk.RevurderingOpprettet(opprettetRevurdering),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for revurdering sendt til attestering på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
        val beregning = TestBeregning
        val behandlingsperiode = Periode.create(1.januar(2021), 31.desember(2021))

        val behandlingMock = mock<Behandling> {
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(49L)
        }
        val revurderingTilAttestering = RevurderingTilAttestering.Innvilget(
            id = UUID.randomUUID(),
            opprettet = LocalDate.now(clock).atStartOfDay().toTidspunkt(zoneIdOslo),
            tilRevurdering = mock {
                on { behandling } doReturn behandlingMock
                on { id } doReturn UUID.randomUUID()
            },
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            periode = behandlingsperiode,
            beregning = beregning,
            simulering = Simulering(
                gjelderId = FnrGenerator.random(),
                gjelderNavn = "Mr. Asd",
                datoBeregnet = LocalDate.now(clock),
                nettoBeløp = 100,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("55"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = RevurderingTestUtils.søknadsbehandlingVedtak.behandlingsinformasjon,
        )

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
            saksbehandler = "saksbehandler",
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            relatertBehandlingId = revurderingTilAttestering.tilRevurdering.id,
            avsluttet = false,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.RevurderingStatistikk.RevurderingTilAttestering(revurderingTilAttestering),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for iverksetting av revurdering på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
        val beregning = TestBeregning
        val behandlingsperiode = Periode.create(1.januar(2021), 31.desember(2021))

        val behandlingMock = mock<Behandling> {
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(49L)
        }
        val iverksattRevurdering = IverksattRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = behandlingsperiode,
            opprettet = LocalDate.now(clock).atStartOfDay().toTidspunkt(zoneIdOslo),
            tilRevurdering = mock {
                on { id } doReturn UUID.randomUUID()
                on { behandling } doReturn behandlingMock
            },
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            oppgaveId = OppgaveId("55"),
            beregning = beregning,
            simulering = Simulering(
                gjelderId = FnrGenerator.random(),
                gjelderNavn = "Mr. Asd",
                datoBeregnet = LocalDate.now(clock),
                nettoBeløp = 100,
                periodeList = listOf(),
            ),
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant")),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = RevurderingTestUtils.søknadsbehandlingVedtak.behandlingsinformasjon,
        )

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
            versjon = clock.millis(),
            saksbehandler = "saksbehandler",
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            relatertBehandlingId = iverksattRevurdering.tilRevurdering.id,
            resultat = "Innvilget",
            resultatBegrunnelse = "Endring i søkers inntekt",
            beslutter = "attestant",
            avsluttet = true,
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.RevurderingStatistikk.RevurderingIverksatt(iverksattRevurdering),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for mottat søknad på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
        val saksnummer = Saksnummer(2049L)
        val søknad = Søknad.Ny(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        )

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

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.SøknadStatistikk.SøknadMottatt(søknad, saksnummer),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }

    @Test
    fun `publiserer statistikk for lukket søknad på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
        val saksnummer = Saksnummer(2049L)
        val søknad = Søknad.Lukket(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId("journalpostid"),
            oppgaveId = OppgaveId("oppgaveid"),
            lukketTidspunkt = Tidspunkt.now(fixedClock),
            lukketAv = NavIdentBruker.Saksbehandler("Mr Lukker"),
            lukketType = Søknad.Lukket.LukketType.AVVIST,
            lukketJournalpostId = JournalpostId("journalpostid"),
            lukketBrevbestillingId = BrevbestillingId("brevbestillingid"),
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

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.SøknadStatistikk.SøknadLukket(søknad, saksnummer),
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) },
        )
    }
}
