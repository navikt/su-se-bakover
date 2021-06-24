package no.nav.su.se.bakover.service.statistikk

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.beregning.TestBeregningSomGirOpphør
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.util.UUID

internal class SøknadsbehandlingStatistikkMapperTest {

    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, zoneIdOslo)

    @Test
    fun `mapper opprettet behandling`() {
        SøknadsbehandlingStatistikkMapper(fixedClock).map(uavklartSøknadsbehandling) shouldBe Statistikk.Behandling(
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
        SøknadsbehandlingStatistikkMapper(fixedClock).map(iverksattSøknadsbehandling) shouldBe Statistikk.Behandling(
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
            beslutter = iverksattSøknadsbehandling.attestering.attestant.navIdent,
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
            SøknadsbehandlingStatistikkMapper(fixedClock).map(beregnetSøknadsbehandling)
        }
    }

    @Nested
    inner class FunksjonellTidMapperTest {
        @Test
        fun `funksjonell til settes til opprettet for behandling dersom beregning ikke er tilgjengelig`() {
            SøknadsbehandlingStatistikkMapper.FunksjonellTidMapper.map(uavklartSøknadsbehandling) shouldBe uavklartSøknadsbehandling.opprettet
            SøknadsbehandlingStatistikkMapper.FunksjonellTidMapper.map(avslåttBeregnetSøknadsbehandling) shouldBe avslåttBeregnetSøknadsbehandling.opprettet
        }

        @Test
        fun `håndterer spesialtilfelle for uavklart behandling uten stønadsperiode`() {
            SøknadsbehandlingStatistikkMapper.FunksjonellTidMapper.map(uavklartSøknadsbehandling.copy(stønadsperiode = null)) shouldBe uavklartSøknadsbehandling.opprettet
        }

        @Test
        fun `funksjonell til settes til dato for beregning dersom tilgjengelig`() {
            SøknadsbehandlingStatistikkMapper.FunksjonellTidMapper.map(tilAttesteringSøknadsbehandling) shouldBe tilAttesteringSøknadsbehandling.periode
                .fraOgMed.startOfDay(zoneIdOslo)
            SøknadsbehandlingStatistikkMapper.FunksjonellTidMapper.map(iverksattSøknadsbehandling) shouldBe tilAttesteringSøknadsbehandling.periode
                .fraOgMed.startOfDay(zoneIdOslo)
        }
    }

    @Nested
    inner class ResultatOgBegrunnelseMapperTest {
        @Test
        fun `mapper resultat og begrunnelse`() {
            SøknadsbehandlingStatistikkMapper.ResultatOgBegrunnelseMapper.map(iverksattSøknadsbehandling) shouldBe SøknadsbehandlingStatistikkMapper.ResultatOgBegrunnelseMapper.ResultatOgBegrunnelse(
                resultat = "Innvilget",
                begrunnelse = null,
            )
        }

        @Test
        fun `mapper ikke ukjente typer`() {
            assertThrows<ManglendeStatistikkMappingException> {
                SøknadsbehandlingStatistikkMapper.ResultatOgBegrunnelseMapper.map(beregnetSøknadsbehandling)
            }
        }
    }

    @Nested
    inner class RegistrertDatoMapperTest {
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
            SøknadsbehandlingStatistikkMapper.RegistrertDatoMapper.map(uavklartSøknadsbehandling.copy(søknad = papirsøknad)) shouldBe expected
        }

        @Test
        fun `registrert dato settes til dato for opprettelse av behandling ved digital søknad`() {
            SøknadsbehandlingStatistikkMapper.RegistrertDatoMapper.map(uavklartSøknadsbehandling) shouldBe uavklartSøknadsbehandling.opprettet.toLocalDate(
                zoneIdOslo,
            )
        }
    }

    @Nested
    inner class BehandlingStatusOgBehandlingStatusBegrunnelseMapperTest {
        @Test
        fun `mapper status og begrunnelse`() {
            SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.OPPRETTET,
            ) shouldBe SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.OPPRETTET, "Ny søknadsbehandling opprettet",
            )

            SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            ) shouldBe SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.TIL_ATTESTERING_INNVILGET, "Innvilget søkndsbehandling sendt til attestering",
            )

            SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.TIL_ATTESTERING_AVSLAG,
            ) shouldBe SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.TIL_ATTESTERING_AVSLAG, "Avslått søknadsbehanding sendt til attestering",
            )

            SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.UNDERKJENT_INNVILGET,
            ) shouldBe SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.UNDERKJENT_INNVILGET,
                "Innvilget søknadsbehandling sendt tilbake fra attestant til saksbehandler",
            )

            SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.UNDERKJENT_AVSLAG,
            ) shouldBe SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.UNDERKJENT_AVSLAG,
                "Avslått søknadsbehandling sendt tilbake fra attestant til saksbehandler",
            )

            SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.IVERKSATT_INNVILGET,
            ) shouldBe SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.IVERKSATT_INNVILGET, "Innvilget søknadsbehandling iverksatt",
            )

            SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                BehandlingsStatus.IVERKSATT_AVSLAG,
            ) shouldBe SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.BehandlingStatusOgBehandlingStatusBeskrivelse(
                BehandlingsStatus.IVERKSATT_AVSLAG, "Avslått søknadsbehandling iverksatt",
            )

            assertThrows<ManglendeStatistikkMappingException> {
                SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.VILKÅRSVURDERT_AVSLAG,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.BEREGNET_AVSLAG,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.BEREGNET_INNVILGET,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
                    BehandlingsStatus.SIMULERT,
                )
            }
            assertThrows<ManglendeStatistikkMappingException> {
                SøknadsbehandlingStatistikkMapper.BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(
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

    private val uavklartSøknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(fixedClock),
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        søknad = søknad,
        oppgaveId = OppgaveId(""),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random(),
        fritekstTilBrev = "",
        stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021))),
        grunnlagsdata = Grunnlagsdata.EMPTY,
        vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
    )

    private val beregning = TestBeregning
    private val avslagBeregning = TestBeregningSomGirOpphør
    private val avslåttBeregnetSøknadsbehandling = (
        uavklartSøknadsbehandling.tilVilkårsvurdert(
            Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        )
            .tilBeregnet(avslagBeregning) as Søknadsbehandling.Beregnet.Avslag
        )
        .tilAttestering(NavIdentBruker.Saksbehandler("jonny"), "")
    private val beregnetSøknadsbehandling = uavklartSøknadsbehandling.tilBeregnet(beregning)
    private val simulertSøknadsbehandling = beregnetSøknadsbehandling.tilSimulert(mock())
    private val tilAttesteringSøknadsbehandling =
        simulertSøknadsbehandling.tilAttestering(NavIdentBruker.Saksbehandler("saks"), "")
    private val iverksattSøknadsbehandling = tilAttesteringSøknadsbehandling.tilIverksatt(
        Attestering.Iverksatt(
            NavIdentBruker.Attestant("att"),
        ),
    )
}
