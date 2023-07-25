package no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.søknadsbehandling.KanSendesTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.beregnetAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.beregnetInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.fritekstTilBrev
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksattAvslagBeregning
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksattAvslagVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksattInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.lukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprettet
import no.nav.su.se.bakover.domain.søknadsbehandling.simulert
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttesteringAvslagBeregning
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttesteringAvslagVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttesteringInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjentAvslagBeregning
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjentAvslagVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjentInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkårsvurdertAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingshendelse
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class TilAttesteringTest {

    @Test
    fun `kaster excepiton hvis innvilget simulering inneholder feilutbetalinger`() {
        val simulertMedFeilutbetaling = simulertSøknadsbehandling().let { (_, søknadsbehandling) ->
            søknadsbehandling.copy(
                simulering = simuleringFeilutbetaling(
                    søknadsbehandling.beregning.getMånedsberegninger().first().periode,
                ),
            )
        }
        assertThrows<IllegalStateException> {
            simulertMedFeilutbetaling.tilAttestering(
                saksbehandler = saksbehandler,
                fritekstTilBrev = fritekstTilBrev,
                clock = fixedClock,
            )
        }

        val underkjentInnvilgetMedFeilutbetaling =
            søknadsbehandlingUnderkjentInnvilget().let { (_, søknadsbehandling) ->
                søknadsbehandling.copy(
                    simulering = simuleringFeilutbetaling(
                        søknadsbehandling.beregning.getMånedsberegninger().first().periode,
                    ),
                )
            }
        assertThrows<IllegalStateException> {
            underkjentInnvilgetMedFeilutbetaling.tilAttestering(
                saksbehandler = saksbehandler,
                fritekstTilBrev = fritekstTilBrev,
                clock = fixedClock,
            )
        }
    }

    @Test
    fun `ulovlige overganger`() {
        listOf(
            opprettet,
            vilkårsvurdertInnvilget,
            beregnetInnvilget,
            tilAttesteringInnvilget,
            tilAttesteringAvslagBeregning,
            tilAttesteringAvslagVilkår,
            iverksattAvslagBeregning,
            iverksattAvslagVilkår,
            iverksattInnvilget,
            lukketSøknadsbehandling,
        ).forEach {
            it shouldNotBe beOfType<KanSendesTilAttestering>()
        }
    }

    @Test
    fun `vilkårsvurder avslag til attestering`() {
        val søknadsbehandling = vilkårsvurdertAvslag
        søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
            clock = fixedClock,
        ).getOrFail().let {
            it shouldBe beOfType<SøknadsbehandlingTilAttestering.Avslag.UtenBeregning>()
            it.saksbehandler shouldBe saksbehandler
            it.fritekstTilBrev shouldBe fritekstTilBrev
            it.søknadsbehandlingsHistorikk shouldBe søknadsbehandling.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                ),
            )
        }
    }

    @Test
    fun `vilkårsvurder beregning til attestering`() {
        val søknadsbehandling = beregnetAvslag
        søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
            clock = fixedClock,
        ).getOrFail().let {
            it shouldBe beOfType<SøknadsbehandlingTilAttestering.Avslag.MedBeregning>()
            it.saksbehandler shouldBe saksbehandler
            it.fritekstTilBrev shouldBe fritekstTilBrev
            it.søknadsbehandlingsHistorikk shouldBe søknadsbehandling.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                ),
            )
        }
    }

    @Test
    fun `simulert til attestering`() {
        val søknadsbehandling = simulert
        søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
            clock = fixedClock,
        ).getOrFail().let {
            it shouldBe beOfType<SøknadsbehandlingTilAttestering.Innvilget>()
            it.saksbehandler shouldBe saksbehandler
            it.fritekstTilBrev shouldBe fritekstTilBrev
            it.søknadsbehandlingsHistorikk shouldBe søknadsbehandling.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                ),
            )
        }
    }

    @Test
    fun `underkjent avslag vilkår til attestering`() {
        val søknadsbehandling = underkjentAvslagVilkår
        søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
            clock = fixedClock,
        ).getOrFail().let {
            it shouldBe beOfType<SøknadsbehandlingTilAttestering.Avslag.UtenBeregning>()
            it.saksbehandler shouldBe saksbehandler
            it.fritekstTilBrev shouldBe fritekstTilBrev
            it.søknadsbehandlingsHistorikk shouldBe søknadsbehandling.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                ),
            )
        }
    }

    @Test
    fun `underkjent avslag beregning til attestering`() {
        val søknadsbehandling = underkjentAvslagBeregning
        søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
            clock = fixedClock,
        ).getOrFail().let {
            it shouldBe beOfType<SøknadsbehandlingTilAttestering.Avslag.MedBeregning>()
            it.saksbehandler shouldBe saksbehandler
            it.fritekstTilBrev shouldBe fritekstTilBrev
            it.søknadsbehandlingsHistorikk shouldBe søknadsbehandling.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                ),
            )
        }
    }

    @Test
    fun `underkjent innvilging til attestering`() {
        val søknadsbehandling = underkjentInnvilget
        søknadsbehandling.tilAttestering(
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
            clock = fixedClock,
        ).getOrFail().let {
            it shouldBe beOfType<SøknadsbehandlingTilAttestering.Innvilget>()
            it.saksbehandler shouldBe saksbehandler
            it.fritekstTilBrev shouldBe fritekstTilBrev
            it.søknadsbehandlingsHistorikk shouldBe søknadsbehandling.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                ),
            )
        }
    }
}
