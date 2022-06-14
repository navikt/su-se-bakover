package no.nav.su.se.bakover.web.komponenttest

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
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
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
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
        val tisdpunktForRevurdering: Clock = 21.mai(2021).fixedClock()
        val clock = TikkendeKlokke(tisdpunktForRevurdering)
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
            ).let { søknadsbehandlingJson ->
                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

                opprettIverksattRevurdering(
                    sakId = sakId,
                    fraOgMed = opphørStartDato.toString(),
                    tilOgMed = behandlingSluttDato.toString(),
                    utenlandsOpphold = UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet,
                )
                UUID.fromString(sakId)
            }

            // Sjekk at Revurderingen førte til Opphør med Avkorting
            appComponents.databaseRepos.avkortingsvarselRepo.hentUtestående(sakId) shouldBe beOfType<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>()

            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = nyBehandlingStartDato.toString(),
                tilOgMed = nyBehandlingSluttDato.toString(),
            )

            val saken = appComponents.services.sak.hentSak(sakId = sakId).getOrFail()

            saken.vedtakListe.let {
                it.size shouldBe 3
                it[0].shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling>().periode shouldBe Periode.create(
                    behandlingStartDato,
                    behandlingSluttDato,
                )
                it[1].shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering>().periode shouldBe Periode.create(
                    opphørStartDato,
                    behandlingSluttDato,
                )
                it[2].shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling>().periode shouldBe Periode.create(
                    nyBehandlingStartDato,
                    nyBehandlingSluttDato,
                )
            }

            saken.utbetalingstidslinje(
                Periode.create(
                    behandlingStartDato,
                    nyBehandlingSluttDato,
                ),
            ).tidslinje.let { utbetalingstidslinje ->
                utbetalingstidslinje[0].shouldBeType<UtbetalingslinjePåTidslinje.Ny>().let {
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
            saken.søknadsbehandlinger.last().grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag.filter {
                it.fradragstype == Fradragstype.AvkortingUtenlandsopphold
            }.size shouldBe 1

            // Sjekk at Avkorting nå er håndtert
            appComponents.databaseRepos.avkortingsvarselRepo.hentUtestående(sakId) shouldBe beOfType<Avkortingsvarsel.Ingen>()
        }
    }
}
