package no.nav.su.se.bakover.domain.søknadsbehandling.beregn

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import io.kotest.matchers.types.shouldNotBeTypeOf
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.FRITEKST_TIL_BREV
import no.nav.su.se.bakover.domain.søknadsbehandling.KanBeregnes
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.beregnetAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.beregnetInnvilget
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
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingshendelse
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.util.UUID

internal class SøknadsbehandlingBeregnTest {

    @Test
    fun `kan beregne for vilkårsvurdert innvilget`() {
        val vilkårsvurdertInnvilget = vilkårsvurdertInnvilget
        vilkårsvurdertInnvilget.beregn(
            begrunnelse = "123",
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetSøknadsbehandling.Innvilget>()
            it.saksbehandler shouldBe saksbehandler
            it.beregning.getBegrunnelse() shouldBe "123"
            it.søknadsbehandlingsHistorikk shouldBe vilkårsvurdertInnvilget.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            )
        }
    }

    @Test
    fun `kan beregne på nytt for beregnet innvilget`() {
        val beregnetInnvilget = beregnetInnvilget
        beregnetInnvilget.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
        ).getOrFail() shouldBe beregnetInnvilget.copy(
            søknadsbehandlingsHistorikk = beregnetInnvilget.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            ),
        )
    }

    @Test
    fun `kan beregne på nytt for beregnet avslag`() {
        val beregnetAvslag = beregnetAvslag
        beregnetAvslag.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
        ).getOrFail() shouldBe beregnetAvslag.copy(
            søknadsbehandlingsHistorikk = beregnetAvslag.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            ),
        )
    }

    @Test
    fun `kan beregne på nytt for simulert`() {
        val simulert = simulert
        simulert.beregn(
            begrunnelse = "123",
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetSøknadsbehandling.Innvilget>()
            it.saksbehandler shouldBe saksbehandler
            it.beregning.getBegrunnelse() shouldBe "123"
            it.fritekstTilBrev shouldBe ""
            it.attesteringer shouldBe Attesteringshistorikk.empty()
            it.søknadsbehandlingsHistorikk shouldBe simulert.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            )
        }
    }

    @Test
    fun `kan beregne på nytt underkjent avslag med beregning`() {
        val underkjentAvslagBeregning = underkjentAvslagBeregning
        underkjentAvslagBeregning.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetSøknadsbehandling.Avslag>()
            it.saksbehandler shouldBe saksbehandler
            it.fritekstTilBrev shouldBe FRITEKST_TIL_BREV
            it.attesteringer shouldBe Attesteringshistorikk.create(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering()))
            it.søknadsbehandlingsHistorikk shouldBe underkjentAvslagBeregning.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            )
        }
    }

    @Test
    fun `ulovlige statusoverganger`() {
        listOf(
            opprettet,
            vilkårsvurdertAvslag,
            tilAttesteringAvslagBeregning,
            tilAttesteringAvslagVilkår,
            tilAttesteringInnvilget,
            underkjentAvslagVilkår,
            iverksattAvslagBeregning,
            iverksattAvslagVilkår,
            iverksattInnvilget,
            lukketSøknadsbehandling,
        ).forEach {
            it.shouldNotBeTypeOf<KanBeregnes>()
        }
    }

    @Test
    fun `kan beregne på nytt underkjent innvilgelse med beregning`() {
        val underkjentInnvilget = underkjentInnvilget
        underkjentInnvilget.beregn(
            begrunnelse = "123",
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetSøknadsbehandling.Innvilget>()
            it.saksbehandler shouldBe saksbehandler
            it.beregning.getBegrunnelse() shouldBe "123"
            it.fritekstTilBrev shouldBe underkjentInnvilget.fritekstTilBrev
            it.attesteringer shouldBe Attesteringshistorikk.create(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering()))
            it.søknadsbehandlingsHistorikk shouldBe underkjentInnvilget.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            )
        }
    }

    @Test
    fun `beregner med fradrag`() {
        søknadsbehandlingVilkårsvurdertInnvilget().let { (_, vilkårsvurdert) ->
            vilkårsvurdert.oppdaterFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 15000.0,
                            periode = vilkårsvurdert.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
                clock = fixedClock,
            )
        }.getOrFail().let { førBeregning ->
            førBeregning.beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
                nySaksbehandler = saksbehandler,
            ).getOrFail().let { etterBeregning ->
                etterBeregning.beregning.getFradrag() shouldHaveSize 2
                etterBeregning.beregning.getSumFradrag() shouldBe førBeregning.periode.getAntallMåneder() * 15000
                etterBeregning.beregning.getSumYtelse() shouldBe førBeregning.periode.måneder()
                    .sumOf { satsFactoryTestPåDato().høyUføre(it).satsForMånedAvrundet - 15000 }
                etterBeregning.grunnlagsdata shouldBe førBeregning.grunnlagsdata
            }
        }
    }
}
