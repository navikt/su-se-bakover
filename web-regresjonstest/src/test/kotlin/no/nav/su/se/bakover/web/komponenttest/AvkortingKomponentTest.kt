package no.nav.su.se.bakover.web.komponenttest

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørtRevurdering
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

class AvkortingKomponentTest {
    @Test
    fun `opphør pga utland som fører til feilutbetaling, skal avkortes og brukes i ny behandling`() {
        //  |------ Behandling ---------|
        //          |---- Opphør Utland-|
        //             |-- Behandling som bruker avkorting -|

        val fnr = Fnr.generer().toString()
        val tidspunktForRevurdering: Clock = 21.mai(2021).fixedClock()
        val clock = TikkendeKlokke(tidspunktForRevurdering)
        val behandlingStartDato = 1.januar(2021)
        val behandlingSluttDato = 31.desember(2021)
        val opphørStartDato = 1.april(2021)
        val nyBehandlingStartDato = 1.juni(2021)
        val nyBehandlingSluttDato = 31.mai(2022)

        withKomptestApplication(
            clock = clock,
        ) { appComponents ->
            val sakId = opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = behandlingStartDato.toString(),
                tilOgMed = behandlingSluttDato.toString(),
                client = this.client,
                appComponents = appComponents,
            ).let { søknadsbehandlingJson ->
                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

                opprettIverksattRevurdering(
                    sakid = sakId,
                    fraogmed = opphørStartDato.toString(),
                    tilogmed = behandlingSluttDato.toString(),
                    leggTilUtenlandsoppholdRevurdering = { _, behandlingId, fraOgMed, tilOgMed, _ ->
                        leggTilUtenlandsoppholdRevurdering(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            fraOgMed = fraOgMed,
                            tilOgMed = tilOgMed,
                            vurdering = UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet.toString(),
                            client = this.client,
                        )
                    },
                    client = this.client,
                    appComponents = appComponents,
                )
                UUID.fromString(sakId)
            }

            // Sjekk at Revurderingen førte til Opphør med Avkorting
            appComponents.databaseRepos.avkortingsvarselRepo.hentUtestående(sakId) shouldBe beOfType<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>()

            val søknadsbehandlingMedAvkortingId = opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = nyBehandlingStartDato.toString(),
                tilOgMed = nyBehandlingSluttDato.toString(),
                client = this.client,
                appComponents = appComponents,
            ).let {
                BehandlingJson.hentBehandlingId(it)
            }

            val saken = appComponents.services.sak.hentSak(sakId = sakId).getOrFail()

            saken.vedtakListe.let {
                it.size shouldBe 3
                it[0].shouldBeType<VedtakInnvilgetSøknadsbehandling>().periode shouldBe Periode.create(
                    behandlingStartDato,
                    behandlingSluttDato,
                )
                it[1].shouldBeType<VedtakOpphørtRevurdering>().periode shouldBe Periode.create(
                    opphørStartDato,
                    behandlingSluttDato,
                )
                it[2].shouldBeType<VedtakInnvilgetSøknadsbehandling>().periode shouldBe Periode.create(
                    nyBehandlingStartDato,
                    nyBehandlingSluttDato,
                )
            }

            saken.utbetalingstidslinje().let { utbetalingstidslinje ->
                utbetalingstidslinje!![0].shouldBeType<UtbetalingslinjePåTidslinje.Ny>().let {
                    it.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                    it.beløp shouldBe 20946
                }
                utbetalingstidslinje[1].shouldBeType<UtbetalingslinjePåTidslinje.Opphør>().let {
                    it.periode shouldBe mai(2021)
                    it.beløp shouldBe 0
                }
                utbetalingstidslinje[2].shouldBeType<UtbetalingslinjePåTidslinje.Ny>().let {
                    it.periode shouldBe juni(2021)
                    it.beløp shouldBe 1043 // (21989-20946=1043)
                }
                utbetalingstidslinje[3].shouldBeType<UtbetalingslinjePåTidslinje.Ny>().let {
                    it.periode shouldBe Periode.create(1.juli(2021), 31.mai(2022))
                    it.beløp shouldBe 21989
                }
            }

            // Sjekk at den nye behandlingen bruker opp det som ligger til avkorting
            saken.søknadsbehandlinger.single { it.id == UUID.fromString(søknadsbehandlingMedAvkortingId) }.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag.filter {
                it.fradragstype == Fradragstype.AvkortingUtenlandsopphold
            }.size shouldBe 1

            // Sjekk at Avkorting nå er håndtert
            appComponents.databaseRepos.avkortingsvarselRepo.hentUtestående(sakId) shouldBe beOfType<Avkortingsvarsel.Ingen>()
        }
    }
}
