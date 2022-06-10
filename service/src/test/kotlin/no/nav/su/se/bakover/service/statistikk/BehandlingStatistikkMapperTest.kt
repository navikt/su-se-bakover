package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.SøknadsinnholdUføre
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.service.statistikk.mappers.BehandlingStatistikkMapper
import no.nav.su.se.bakover.service.statistikk.mappers.ManglendeStatistikkMappingException
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTest
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.simulerNyUtbetaling
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingLukket
import no.nav.su.se.bakover.test.søknadsbehandlingSimulert
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class BehandlingStatistikkMapperTest {

    @Test
    fun `mapper ny søknad`() {
        val saksnummer = Saksnummer(2079L)
        val søknad = Søknad.Ny(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        )

        BehandlingStatistikkMapper(fixedClock).map(
            søknad,
            saksnummer,
            Statistikk.Behandling.SøknadStatus.SØKNAD_MOTTATT,
        ) shouldBe Statistikk.Behandling(
            funksjonellTid = søknad.opprettet,
            tekniskTid = søknad.opprettet,
            mottattDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknad.id,
            relatertBehandlingId = null,
            sakId = søknad.sakId,
            saksnummer = saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = "Søknad for SU Uføre",
            behandlingStatus = "SØKNAD_MOTTATT",
            behandlingStatusBeskrivelse = "Søknaden er mottatt",
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = false,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = null,
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = null,
            saksbehandler = null,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = false,
        )
    }

    @Test
    fun `mapper lukket søknad`() {
        val saksnummer = Saksnummer(2079L)
        val søknad = Søknad.Journalført.MedOppgave.Lukket(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId("journalpostid"),
            oppgaveId = OppgaveId("oppgaveid"),
            lukketTidspunkt = fixedTidspunkt,
            lukketAv = NavIdentBruker.Saksbehandler(navIdent = "Mr Lukker"),
            lukketType = Søknad.Journalført.MedOppgave.Lukket.LukketType.AVVIST,
        )

        BehandlingStatistikkMapper(fixedClock).map(
            søknad,
            saksnummer,
            Statistikk.Behandling.SøknadStatus.SØKNAD_LUKKET,
        ) shouldBe Statistikk.Behandling(
            funksjonellTid = søknad.lukketTidspunkt,
            tekniskTid = søknad.lukketTidspunkt,
            mottattDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknad.id,
            relatertBehandlingId = null,
            sakId = søknad.sakId,
            saksnummer = saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = "Søknad for SU Uføre",
            behandlingStatus = "SØKNAD_LUKKET",
            behandlingStatusBeskrivelse = "Søknaden er lukket",
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = false,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = "AVVIST",
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = null,
            saksbehandler = "Mr Lukker",
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = true,
        )
    }

    @Test
    fun `mapper opprettet behandling`() {
        BehandlingStatistikkMapper(fixedClock).map(uavklartSøknadsbehandling) shouldBe Statistikk.Behandling(
            funksjonellTid = uavklartSøknadsbehandling.opprettet,
            tekniskTid = fixedTidspunkt,
            mottattDato = uavklartSøknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = uavklartSøknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = uavklartSøknadsbehandling.id,
            relatertBehandlingId = null,
            sakId = uavklartSøknadsbehandling.sakId,
            søknadId = uavklartSøknadsbehandling.søknad.id,
            saksnummer = uavklartSøknadsbehandling.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = "Søknad for SU Uføre",
            behandlingStatus = BehandlingsStatus.OPPRETTET.toString(),
            behandlingStatusBeskrivelse = "Ny søknadsbehandling opprettet",
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = true,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = null,
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = null,
            saksbehandler = null,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = false,
        )
    }

    @Test
    fun `mapper iverksatt behandling`() {
        BehandlingStatistikkMapper(fixedClock).map(iverksattSøknadsbehandling) shouldBe Statistikk.Behandling(
            funksjonellTid = fixedTidspunkt,
            tekniskTid = fixedTidspunkt,
            mottattDato = iverksattSøknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = iverksattSøknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = iverksattSøknadsbehandling.id,
            relatertBehandlingId = null,
            sakId = iverksattSøknadsbehandling.sakId,
            søknadId = iverksattSøknadsbehandling.søknad.id,
            saksnummer = iverksattSøknadsbehandling.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = "Søknad for SU Uføre",
            behandlingStatus = BehandlingsStatus.IVERKSATT_INNVILGET.toString(),
            behandlingStatusBeskrivelse = "Innvilget søknadsbehandling iverksatt",
            behandlingYtelseDetaljer = listOf(Statistikk.BehandlingYtelseDetaljer(Statistikk.Stønadsklassifisering.BOR_ALENE)),
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = true,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = "Innvilget",
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = iverksattSøknadsbehandling.attesteringer.hentSisteAttestering().attestant.navIdent,
            saksbehandler = iverksattSøknadsbehandling.saksbehandler.navIdent,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = true,
        )
    }

    @Test
    fun `mapper ikke ukjente typer`() {
        assertThrows<ManglendeStatistikkMappingException> {
            BehandlingStatistikkMapper(fixedClock).map(beregnetSøknadsbehandling)
        }
    }

    @Test
    fun `mapper opprettet revurdering`() {
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        BehandlingStatistikkMapper(fixedClock).map(opprettetRevurdering) shouldBe Statistikk.Behandling(
            funksjonellTid = opprettetRevurdering.opprettet,
            tekniskTid = fixedTidspunkt,
            mottattDato = opprettetRevurdering.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = opprettetRevurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = opprettetRevurdering.id,
            relatertBehandlingId = opprettetRevurdering.tilRevurdering.id,
            sakId = opprettetRevurdering.sakId,
            søknadId = null,
            saksnummer = opprettetRevurdering.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            behandlingStatus = "OPPRETTET",
            behandlingStatusBeskrivelse = "Ny revurdering opprettet",
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = true,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = null,
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = null,
            saksbehandler = opprettetRevurdering.saksbehandler.navIdent,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = false,
        )
    }

    @Test
    fun `mapper iverksatt revurdering`() {
        val iverksattRevurdering = iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        BehandlingStatistikkMapper(fixedClock).map(iverksattRevurdering) shouldBe Statistikk.Behandling(
            funksjonellTid = iverksattRevurdering.opprettet,
            tekniskTid = fixedTidspunkt,
            mottattDato = iverksattRevurdering.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = iverksattRevurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = iverksattRevurdering.id,
            relatertBehandlingId = iverksattRevurdering.tilRevurdering.id,
            sakId = iverksattRevurdering.sakId,
            søknadId = null,
            saksnummer = iverksattRevurdering.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            behandlingStatus = "IVERKSATT_INNVILGET",
            behandlingStatusBeskrivelse = "Innvilget revurdering iverksatt",
            behandlingYtelseDetaljer = listOf(Statistikk.BehandlingYtelseDetaljer(Statistikk.Stønadsklassifisering.BOR_ALENE)),
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = true,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = "Innvilget",
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = iverksattRevurdering.attestering.attestant.navIdent,
            saksbehandler = iverksattRevurdering.saksbehandler.navIdent,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = true,
        )
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `mapper uendret iverksettinger`() {
        val iverksattRevurdering = iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak().second

        BehandlingStatistikkMapper(fixedClock).map(iverksattRevurdering) shouldBe Statistikk.Behandling(
            funksjonellTid = iverksattRevurdering.opprettet,
            tekniskTid = fixedTidspunkt,
            mottattDato = iverksattRevurdering.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = iverksattRevurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = iverksattRevurdering.id,
            relatertBehandlingId = iverksattRevurdering.tilRevurdering.id,
            sakId = iverksattRevurdering.sakId,
            søknadId = null,
            saksnummer = iverksattRevurdering.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            behandlingStatus = "IVERKSATT_INGEN_ENDRING",
            behandlingStatusBeskrivelse = "Revurdering uten endring i ytelse iverksatt",
            behandlingYtelseDetaljer = listOf(Statistikk.BehandlingYtelseDetaljer(Statistikk.Stønadsklassifisering.BOR_ALENE)),
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = true,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = "Uendret",
            resultatBegrunnelse = "Mindre enn 10% endring i inntekt",
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = iverksattRevurdering.attestering.attestant.navIdent,
            saksbehandler = iverksattRevurdering.saksbehandler.navIdent,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = true,
        )
    }

    @Test
    fun `mapper gjenopptak`() {
        val gjenopptak = iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse(år(2021)).second
        BehandlingStatistikkMapper(fixedClock).map(gjenopptak) shouldBe Statistikk.Behandling(
            funksjonellTid = gjenopptak.opprettet,
            tekniskTid = fixedTidspunkt,
            mottattDato = gjenopptak.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = gjenopptak.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = gjenopptak.id,
            relatertBehandlingId = gjenopptak.tilRevurdering.id,
            sakId = gjenopptak.sakId,
            søknadId = null,
            saksnummer = gjenopptak.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            behandlingStatus = "IVERKSATT_GJENOPPTAK",
            behandlingStatusBeskrivelse = "Ytelsen er gjenopptatt",
            behandlingYtelseDetaljer = null,
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = false,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = "Gjenopptatt",
            resultatBegrunnelse = "Mottatt kontrollerklæring",
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = "attestant",
            saksbehandler = null,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = false,
        )
    }

    @Test
    fun `mapper stans`() {
        val stans = iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(periode = år(2021)).second
        BehandlingStatistikkMapper(fixedClock).map(stans) shouldBe Statistikk.Behandling(
            funksjonellTid = stans.opprettet,
            tekniskTid = fixedTidspunkt,
            mottattDato = stans.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = stans.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = stans.id,
            relatertBehandlingId = stans.tilRevurdering.id,
            sakId = stans.sakId,
            søknadId = null,
            saksnummer = stans.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            behandlingStatus = "IVERKSATT_STANS",
            behandlingStatusBeskrivelse = "Ytelsen er stanset",
            behandlingYtelseDetaljer = null,
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = false,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = "Stanset",
            resultatBegrunnelse = "Manglende kontrollerklæring",
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = "attestant",
            saksbehandler = null,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = false,
        )
    }

    @Test
    fun `mapper oversendt klage`() {
        val klage = oversendtKlage(opprettet = fixedTidspunkt).second
        BehandlingStatistikkMapper(fixedClock).map(klage) shouldBe Statistikk.Behandling(
            funksjonellTid = klage.opprettet,
            tekniskTid = fixedTidspunkt,
            mottattDato = 1.desember(2021),
            registrertDato = klage.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = klage.id,
            relatertBehandlingId = klage.vilkårsvurderinger.vedtakId,
            sakId = klage.sakId,
            søknadId = null,
            saksnummer = klage.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.KLAGE,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.KLAGE.beskrivelse,
            behandlingStatus = "OVERSENDT",
            behandlingStatusBeskrivelse = "Klagen er oversendt til klageinstans",
            behandlingYtelseDetaljer = null,
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = true,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = "Opprettholdt",
            resultatBegrunnelse = "Opprettholdt i henhold til lov om supplerende stønad kapittel 2 - § 3, kapittel 2 - § 4.",
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = "attestant",
            saksbehandler = "saksbehandler",
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = false,
        )
    }

    @Nested
    inner class ResultatOgBegrunnelseMapperTest {
        @Test
        fun `mapper resultat og begrunnelse`() {
            BehandlingStatistikkMapper.ResultatOgBegrunnelseMapper.map(iverksattSøknadsbehandling) shouldBe BehandlingStatistikkMapper.ResultatOgBegrunnelse(
                resultat = "Innvilget",
                begrunnelse = null,
            )
        }

        @Test
        fun `mapper ikke ukjente typer`() {
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.ResultatOgBegrunnelseMapper.map(beregnetSøknadsbehandling)
            }
        }
    }

    @Nested
    inner class MottattDatoMapperTest {
        @Test
        fun `registrert dato settes til dato for mottak av papirsøknad`() {
            val expected = 1.februar(2021)
            val papirsøknad = søknad.copy(
                søknadInnhold = when (søknad.søknadInnhold) {
                    is SøknadsinnholdAlder -> (søknad.søknadInnhold as SøknadsinnholdAlder).copy(
                        forNav = ForNav.Papirsøknad(
                            mottaksdatoForSøknad = expected,
                            grunnForPapirinnsending = ForNav.Papirsøknad.GrunnForPapirinnsending.MidlertidigUnntakFraOppmøteplikt,
                            annenGrunn = "",
                        ),
                    )
                    is SøknadsinnholdUføre -> (søknad.søknadInnhold as SøknadsinnholdUføre).copy(
                        forNav = ForNav.Papirsøknad(
                            mottaksdatoForSøknad = expected,
                            grunnForPapirinnsending = ForNav.Papirsøknad.GrunnForPapirinnsending.MidlertidigUnntakFraOppmøteplikt,
                            annenGrunn = "",
                        ),
                    )
                },
            )
            BehandlingStatistikkMapper.MottattDatoMapper.map(uavklartSøknadsbehandling.copy(søknad = papirsøknad)) shouldBe expected
        }

        @Test
        fun `registrert dato settes til dato for opprettelse av behandling ved digital søknad`() {
            BehandlingStatistikkMapper.MottattDatoMapper.map(uavklartSøknadsbehandling) shouldBe uavklartSøknadsbehandling.opprettet.toLocalDate(
                zoneIdOslo,
            )
        }
    }

    @Nested
    inner class BehandlingStatusOgBehandlingStatusBegrunnelseMapperTest {
        @Test
        fun `mapper status og begrunnelse`() {
            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                søknadsbehandlingVilkårsvurdertUavklart().second,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                "OPPRETTET", "Ny søknadsbehandling opprettet",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                søknadsbehandlingTilAttesteringInnvilget().second,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                "TIL_ATTESTERING_INNVILGET", "Innvilget søkndsbehandling sendt til attestering",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                søknadsbehandlingTilAttesteringAvslagMedBeregning().second,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                "TIL_ATTESTERING_AVSLAG", "Avslått søknadsbehanding sendt til attestering",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                søknadsbehandlingUnderkjentInnvilget().second,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                "UNDERKJENT_INNVILGET",
                "Innvilget søknadsbehandling sendt tilbake fra attestant til saksbehandler",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                søknadsbehandlingUnderkjentAvslagUtenBeregning().second,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                "UNDERKJENT_AVSLAG",
                "Avslått søknadsbehandling sendt tilbake fra attestant til saksbehandler",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                søknadsbehandlingIverksattInnvilget().second,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                "IVERKSATT_INNVILGET", "Innvilget søknadsbehandling iverksatt",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                søknadsbehandlingIverksattAvslagMedBeregning().second,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                "IVERKSATT_AVSLAG", "Avslått søknadsbehandling iverksatt",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                søknadsbehandlingLukket().second,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                "LUKKET", "Søknadsbehandling er lukket",
            )

            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    søknadsbehandlingVilkårsvurdertAvslag().second,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    søknadsbehandlingVilkårsvurdertInnvilget().second,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    søknadsbehandlingBeregnetAvslag().second,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    søknadsbehandlingBeregnetInnvilget().second,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    søknadsbehandlingSimulert().second,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    søknadsbehandlingVilkårsvurdertInnvilget().second,
                )
            }
        }
    }

    private val søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        sakId = UUID.randomUUID(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        journalpostId = JournalpostId(""),
        oppgaveId = OppgaveId(""),
    )

    val stønadsperiode = Stønadsperiode.create(år(2021))

    private val uavklartSøknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second

    private val sakOgInnvilget = søknadsbehandlingVilkårsvurdertInnvilget()
    private val vilkårsvurdertInnvilgetSøknadsbehandling = sakOgInnvilget.second
    private val beregnetSøknadsbehandling = vilkårsvurdertInnvilgetSøknadsbehandling.beregn(
        begrunnelse = null,
        clock = fixedClock,
        satsFactory = satsFactoryTest,
        formuegrenserFactory = formuegrenserFactoryTest,
    ).getOrFail()
    private val simulertSøknadsbehandling = beregnetSøknadsbehandling.simuler(
        saksbehandler = saksbehandler,
    ) {
        simulerNyUtbetaling(
            sak = sakOgInnvilget.first,
            request = it,
            clock = fixedClock,
        )
    }.getOrFail()

    private val tilAttesteringSøknadsbehandling =
        simulertSøknadsbehandling.tilAttestering(NavIdentBruker.Saksbehandler("saks"), "")
    private val iverksattSøknadsbehandling = tilAttesteringSøknadsbehandling.tilIverksatt(
        Attestering.Iverksatt(
            NavIdentBruker.Attestant("att"),
            fixedTidspunkt,
        ),
    )
}
