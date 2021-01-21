package no.nav.su.se.bakover.service.statistikk

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.IverksattBehandling
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.person.PersonService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class StatistikkServiceImplTest {
    private val sakTopicName = "supstonad.aapen-su-sak-statistikk-v1"
    private val behandlingTopicName = "supstonad.aapen-su-behandling-statistikk-v1"

    @Test
    fun `Gyldig sak publiserer till kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }

        StatistikkServiceImpl(kafkaPublisherMock, mock()).publiser(StatistikkSchemaValidatorTest.gyldigSak)
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe sakTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(StatistikkSchemaValidatorTest.gyldigSak) }
        )
    }

    @Test
    fun `Gyldig behandling publiserer till kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }

        StatistikkServiceImpl(kafkaPublisherMock, mock()).publiser(StatistikkSchemaValidatorTest.gyldigBehandling)
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(StatistikkSchemaValidatorTest.gyldigBehandling) }
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
            opprettet = Tidspunkt.now(),
            fnr = FnrGenerator.random(),
            søknader = listOf(),
            behandlinger = listOf(),
            utbetalinger = listOf()
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
            versjon = clock.millis()
        )

        StatistikkServiceImpl(kafkaPublisherMock, personServiceMock, clock).handle(
            Event.Statistikk.SakOpprettet(sak)
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe sakTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) }
        )
        verify(personServiceMock).hentAktørId(
            argThat { it shouldBe sak.fnr }
        )
    }

    @Test
    fun `publiserer BehandlingOpprettet-event på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val søknaasdd: Søknad.Journalført.MedOppgave = mock { on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build() }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)

        val behandling: Behandling = mock {
            on { opprettet } doReturn Tidspunkt.now()
            on { søknad } doReturn søknaasdd
            on { opprettet } doReturn Tidspunkt.now()
            on { id } doReturn UUID.randomUUID()
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(5959)
            on { status() } doReturn Behandling.BehandlingsStatus.OPPRETTET
        }

        val expected = Statistikk.Behandling(
            funksjonellTid = behandling.opprettet,
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = behandling.id,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer.nummer,
            behandlingStatus = behandling.status(),
            versjon = clock.millis(),
            behandlingType = "SOKNAD",
            behandlingTypeBeskrivelse = "Søknad for SU Uføre",
            utenlandstilsnitt = "NASJONAL",
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            avsender = "su-se-bakover",
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.BehandlingOpprettet(behandling)
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) }
        )
    }

    @Test
    fun `publiserer BehandlingTilAttestering-event på kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val søknadMock: Søknad.Journalført.MedOppgave = mock { on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build() }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)

        val behandling: Behandling = mock {
            on { opprettet } doReturn Tidspunkt.now()
            on { søknad } doReturn søknadMock
            on { opprettet } doReturn Tidspunkt.now()
            on { id } doReturn UUID.randomUUID()
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(5959)
            on { status() } doReturn Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
            on { saksbehandler() } doReturn NavIdentBruker.Saksbehandler("Z1595")
            on { attestering() } doReturn Attestering.Iverksatt(NavIdentBruker.Attestant("Z1596"))
            on { utledAvslagsgrunner() } doReturn listOf(Avslagsgrunn.UFØRHET, Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER)
        }

        val expected = Statistikk.Behandling(
            funksjonellTid = behandling.opprettet,
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = behandling.id,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer.nummer,
            behandlingStatus = behandling.status(),
            versjon = clock.millis(),
            saksbehandler = "Z1595",
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.BehandlingTilAttestering(behandling)
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) }
        )
    }

    @Test
    fun `publiserer BehandlingIverksatt-event på kafka ved innvilgelse`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val søknadMock: Søknad.Journalført.MedOppgave = mock { on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build() }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
        val beregning: Beregning = mock {
            on { getPeriode() } doReturn Periode.create(1.januar(2021), 31.januar(2021))
        }

        val behandling: Behandling = mock {
            on { opprettet } doReturn Tidspunkt.now()
            on { søknad } doReturn søknadMock
            on { opprettet } doReturn Tidspunkt.now()
            on { id } doReturn UUID.randomUUID()
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(5959)
            on { status() } doReturn Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
            on { beregning() } doReturn beregning
            on { saksbehandler() } doReturn NavIdentBruker.Saksbehandler("55")
            on { attestering() } doReturn Attestering.Iverksatt(NavIdentBruker.Attestant("56"))
        }

        val expected = Statistikk.Behandling(
            funksjonellTid = 1.januar(2021).startOfDay(zoneIdOslo),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = behandling.id,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer.nummer,
            behandlingStatus = behandling.status(),
            versjon = clock.millis(),
            resultat = "Innvilget",
            saksbehandler = "55",
            beslutter = "56",
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.BehandlingIverksatt(IverksattBehandling.UtenMangler(behandling))
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) }
        )
    }

    @Test
    fun `publiserer BehandlingIverksatt-event på kafka ved avslag`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }
        val søknadMock: Søknad.Journalført.MedOppgave = mock { on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build() }
        val clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)

        val behandling: Behandling = mock {
            on { opprettet } doReturn Tidspunkt.now()
            on { søknad } doReturn søknadMock
            on { opprettet } doReturn Tidspunkt.now()
            on { id } doReturn UUID.randomUUID()
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(5959)
            on { status() } doReturn Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
            on { saksbehandler() } doReturn NavIdentBruker.Saksbehandler("55")
            on { attestering() } doReturn Attestering.Iverksatt(NavIdentBruker.Attestant("56"))
            on { utledAvslagsgrunner() } doReturn listOf(Avslagsgrunn.UFØRHET, Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER)
        }

        val expected = Statistikk.Behandling(
            funksjonellTid = behandling.opprettet,
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = behandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = behandling.id,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer.nummer,
            behandlingStatus = behandling.status(),
            versjon = clock.millis(),
            resultat = "Avslått",
            saksbehandler = "55",
            beslutter = "56",
            resultatBegrunnelse = "UFØRHET,UTENLANDSOPPHOLD_OVER_90_DAGER"
        )

        StatistikkServiceImpl(kafkaPublisherMock, mock(), clock).handle(
            Event.Statistikk.BehandlingIverksatt(IverksattBehandling.UtenMangler(behandling))
        )

        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(expected) }
        )
    }
}
