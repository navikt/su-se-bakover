package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.statistikk.mappers.BehandlingStatistikkMapper
import no.nav.su.se.bakover.service.statistikk.mappers.ManglendeStatistikkMappingException
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Clock
import java.util.UUID

internal class BehandlingStatistikkMapperTest {

    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, zoneIdOslo)
    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )

    @Test
    fun `mapper ny søknad`() {
        val saksnummer = Saksnummer(2079L)
        val søknad = Søknad.Ny(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build()
        )

        BehandlingStatistikkMapper(fixedClock).map(søknad, saksnummer, Statistikk.Behandling.SøknadStatus.SØKNAD_MOTTATT) shouldBe Statistikk.Behandling(
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
        val søknad = Søknad.Lukket(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId("journalpostid"),
            oppgaveId = OppgaveId("oppgaveid"),
            lukketTidspunkt = Tidspunkt.now(fixedClock),
            lukketAv = NavIdentBruker.Saksbehandler(navIdent = "Mr Lukker"),
            lukketType = Søknad.Lukket.LukketType.AVVIST,
        )

        BehandlingStatistikkMapper(fixedClock).map(søknad, saksnummer, Statistikk.Behandling.SøknadStatus.SØKNAD_LUKKET) shouldBe Statistikk.Behandling(
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
            avsluttet = true
        )
    }

    @Test
    fun `mapper opprettet behandling`() {
        BehandlingStatistikkMapper(fixedClock).map(uavklartSøknadsbehandling) shouldBe Statistikk.Behandling(
            funksjonellTid = uavklartSøknadsbehandling.opprettet,
            tekniskTid = Tidspunkt.now(fixedClock),
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
            funksjonellTid = iverksattSøknadsbehandling.periode.fraOgMed.startOfDay(zoneIdOslo),
            tekniskTid = Tidspunkt.now(fixedClock),
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
        val behandlingMock = mock<Behandling> {
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(2021)
        }
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = Periode.create(1.januar(2021), 31.januar(2021)),
            opprettet = Tidspunkt.now(fixedClock),
            tilRevurdering = mock {
                on { behandling } doReturn behandlingMock
                on { id } doReturn UUID.randomUUID()
            },
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "7"),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )

        BehandlingStatistikkMapper(fixedClock).map(opprettetRevurdering) shouldBe Statistikk.Behandling(
            funksjonellTid = opprettetRevurdering.opprettet,
            tekniskTid = Tidspunkt.now(fixedClock),
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
            saksbehandler = "7",
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
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))

        val behandlingMock = mock<Behandling> {
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(2021)
        }
        val iverksattRevurdering = IverksattRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(fixedClock),
            tilRevurdering = mock {
                on { behandling } doReturn behandlingMock
                on { id } doReturn UUID.randomUUID()
            },
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "99"),
            oppgaveId = OppgaveId(value = "7"),
            beregning = mock {
                on { this.periode } doReturn periode
            },
            simulering = mock(),
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(
                    Attestering.Iverksatt(
                        NavIdentBruker.Attestant(navIdent = "2"),
                        Tidspunkt.now(fixedClock),
                    ),
                ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        BehandlingStatistikkMapper(fixedClock).map(iverksattRevurdering) shouldBe Statistikk.Behandling(
            funksjonellTid = iverksattRevurdering.opprettet,
            tekniskTid = Tidspunkt.now(fixedClock),
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
            behandlingYtelseDetaljer = emptyList(),
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
            beslutter = "2",
            saksbehandler = "99",
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = true,
        )
    }

    @Test
    fun `mapper uendret iverksettinger`() {
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))

        val behandlingMock = mock<Behandling> {
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(2021)
        }
        val iverksattRevurdering = IverksattRevurdering.IngenEndring(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(fixedClock),
            tilRevurdering = mock {
                on { behandling } doReturn behandlingMock
                on { id } doReturn UUID.randomUUID()
            },
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "99"),
            oppgaveId = OppgaveId(value = "7"),
            beregning = mock {
                on { this.periode } doReturn periode
            },
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(
                    Attestering.Iverksatt(
                        NavIdentBruker.Attestant(navIdent = "2"),
                        Tidspunkt.now(fixedClock),
                    ),
                ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilBrevutsending = true,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        BehandlingStatistikkMapper(fixedClock).map(iverksattRevurdering) shouldBe Statistikk.Behandling(
            funksjonellTid = iverksattRevurdering.opprettet,
            tekniskTid = Tidspunkt.now(fixedClock),
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
            behandlingYtelseDetaljer = emptyList(),
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
            beslutter = "2",
            saksbehandler = "99",
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = true,
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
                søknadInnhold = søknad.søknadInnhold.copy(
                    forNav = ForNav.Papirsøknad(
                        mottaksdatoForSøknad = expected,
                        grunnForPapirinnsending = ForNav.Papirsøknad.GrunnForPapirinnsending.MidlertidigUnntakFraOppmøteplikt,
                        annenGrunn = "",
                    ),
                ),
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
                BehandlingsStatus.OPPRETTET,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.OPPRETTET, "Ny søknadsbehandling opprettet",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.TIL_ATTESTERING_INNVILGET, "Innvilget søkndsbehandling sendt til attestering",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.TIL_ATTESTERING_AVSLAG,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.TIL_ATTESTERING_AVSLAG, "Avslått søknadsbehanding sendt til attestering",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.UNDERKJENT_INNVILGET,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.UNDERKJENT_INNVILGET,
                "Innvilget søknadsbehandling sendt tilbake fra attestant til saksbehandler",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.UNDERKJENT_AVSLAG,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.UNDERKJENT_AVSLAG,
                "Avslått søknadsbehandling sendt tilbake fra attestant til saksbehandler",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.IVERKSATT_INNVILGET,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.IVERKSATT_INNVILGET, "Innvilget søknadsbehandling iverksatt",
            )

            BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.IVERKSATT_AVSLAG,
            ) shouldBe BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.IVERKSATT_AVSLAG, "Avslått søknadsbehandling iverksatt",
            )

            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.VILKÅRSVURDERT_AVSLAG,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.BEREGNET_AVSLAG,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.BEREGNET_INNVILGET,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.SIMULERT,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                BehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
                )
            }
        }
    }

    private val søknad = Søknad.Journalført.MedOppgave(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(fixedClock),
        sakId = UUID.randomUUID(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        journalpostId = JournalpostId(""),
        oppgaveId = OppgaveId(""),
    )

    val stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021)))
    private val uavklartSøknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(fixedClock),
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        søknad = søknad,
        oppgaveId = OppgaveId(""),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = Fnr.generer(),
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
        vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        attesteringer = Attesteringshistorikk.empty(),
    )

    private val beregning = TestBeregning
    private val beregnetSøknadsbehandling = uavklartSøknadsbehandling.tilBeregnet(beregning)
    private val simulertSøknadsbehandling = beregnetSøknadsbehandling.tilSimulert(mock())
    private val tilAttesteringSøknadsbehandling =
        simulertSøknadsbehandling.tilAttestering(NavIdentBruker.Saksbehandler("saks"), "")
    private val iverksattSøknadsbehandling = tilAttesteringSøknadsbehandling.tilIverksatt(
        Attestering.Iverksatt(
            NavIdentBruker.Attestant("att"),
            Tidspunkt.now(fixedClock)
        ),
    )
}
