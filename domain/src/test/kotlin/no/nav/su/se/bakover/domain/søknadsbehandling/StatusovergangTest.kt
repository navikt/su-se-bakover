package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withAvslåttFlyktning
import no.nav.su.se.bakover.domain.behandling.withVilkårIkkeVurdert
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.innvilgetUførevilkår
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.uføregrunnlagForventetInntekt12000
import no.nav.su.se.bakover.test.utlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class StatusovergangTest {

    private val stønadsperiode = stønadsperiode2021

    private val innvilgetBeregning = beregning(
        periode = stønadsperiode.periode,
        uføregrunnlag = nonEmptyListOf(
            uføregrunnlagForventetInntekt12000(periode = stønadsperiode.periode),
        ),
    )

    private val avslagBeregning = beregning(
        periode = stønadsperiode.periode,
        uføregrunnlag = nonEmptyListOf(
            uføregrunnlagForventetInntekt(periode = stønadsperiode.periode, forventetInntekt = 1000000),
        ),
    )

    private val sakOgUavklart = søknadsbehandlingVilkårsvurdertUavklart(
        stønadsperiode = stønadsperiode,
    )

    private val sak = sakOgUavklart.first

    private val opprettet = sakOgUavklart.second

    private val simulering = no.nav.su.se.bakover.test.simuleringNy()

    private val attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant"), fixedTidspunkt)
    private val utbetalingId = UUID30.randomUUID()

    private val fritekstTilBrev: String = "Fritekst til brev"

    private val vilkårsvurdertInnvilget: Søknadsbehandling.Vilkårsvurdert.Innvilget =
        søknadsbehandlingVilkårsvurdertInnvilget().second
    private val vilkårsvurdertAvslag: Søknadsbehandling.Vilkårsvurdert.Avslag =
        søknadsbehandlingVilkårsvurdertAvslag().second

    private val beregnetInnvilget: Søknadsbehandling.Beregnet.Innvilget =
        vilkårsvurdertInnvilget.tilBeregnet(innvilgetBeregning) as Søknadsbehandling.Beregnet.Innvilget
    private val beregnetAvslag: Søknadsbehandling.Beregnet.Avslag =
        vilkårsvurdertInnvilget.tilBeregnet(avslagBeregning) as Søknadsbehandling.Beregnet.Avslag
    private val simulert: Søknadsbehandling.Simulert =
        beregnetInnvilget.tilSimulert(simulering)
    private val tilAttesteringInnvilget: Søknadsbehandling.TilAttestering.Innvilget =
        simulert.tilAttestering(saksbehandler, fritekstTilBrev)
    private val tilAttesteringAvslagVilkår: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning =
        vilkårsvurdertAvslag.tilAttestering(saksbehandler, fritekstTilBrev)
    private val tilAttesteringAvslagBeregning: Søknadsbehandling.TilAttestering.Avslag.MedBeregning =
        beregnetAvslag.tilAttestering(saksbehandler, fritekstTilBrev)
    private val underkjentInnvilget: Søknadsbehandling.Underkjent.Innvilget =
        tilAttesteringInnvilget.tilUnderkjent(attesteringUnderkjent(clock = fixedClock))
    private val underkjentAvslagVilkår: Søknadsbehandling.Underkjent.Avslag.UtenBeregning =
        tilAttesteringAvslagVilkår.tilUnderkjent(attesteringUnderkjent(clock = fixedClock))
    private val underkjentAvslagBeregning: Søknadsbehandling.Underkjent.Avslag.MedBeregning =
        tilAttesteringAvslagBeregning.tilUnderkjent(attesteringUnderkjent(clock = fixedClock))
    private val iverksattInnvilget = tilAttesteringInnvilget.tilIverksatt(attestering)
    private val iverksattAvslagVilkår =
        tilAttesteringAvslagVilkår.tilIverksatt(attestering)
    private val iverksattAvslagBeregning =
        tilAttesteringAvslagBeregning.tilIverksatt(attestering)
    private val lukketSøknadsbehandling =
        underkjentInnvilget.lukkSøknadsbehandling().orNull()!!

    @Nested
    inner class TilVilkårsvurdert {
        @Test
        fun `opprettet til vilkårsvurdert innvilget`() {
            statusovergang(
                opprettet.copy(
                    grunnlagsdata = vilkårsvurdertInnvilget.grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurdertInnvilget.vilkårsvurderinger,
                ),
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `opprettet til vilkårsvurdert avslag`() {
            statusovergang(
                opprettet.copy(
                    grunnlagsdata = vilkårsvurdertInnvilget.grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurdertInnvilget.vilkårsvurderinger,
                ),
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAvslåttFlyktning(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `opprettet til vilkårsvurdert uavklart (opprettet)`() {
            statusovergang(
                opprettet,
                Statusovergang.TilVilkårsvurdert(
                    withVilkårIkkeVurdert(),
                    fixedClock,
                ),
            ) shouldBe opprettet
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAvslåttFlyktning(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert innvilget`() {
            statusovergang(
                vilkårsvurdertAvslag.copy(
                    grunnlagsdata = Grunnlagsdata.create(bosituasjon = listOf(bosituasjongrunnlagEnslig())),
                    vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                        uføre = innvilgetUførevilkår(),
                        utenlandsopphold = utlandsoppholdInnvilget()
                    ),
                ),
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    fixedClock
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert avslag`() {
            statusovergang(
                vilkårsvurdertAvslag,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAvslåttFlyktning(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    fixedClock
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAvslåttFlyktning(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `beregnet avslag til vilkårsvurdert innvilget`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    fixedClock
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `beregnet avslag til vilkårsvurdert avslag`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAvslåttFlyktning(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `simulert til vilkårsvurdert innvilget`() {
            statusovergang(
                simulert,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    fixedClock
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `simulert til vilkårsvurdert avslag`() {
            statusovergang(
                simulert,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAvslåttFlyktning(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    fixedClock
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAvslåttFlyktning(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentAvslagVilkår.copy(
                    grunnlagsdata = Grunnlagsdata.create(bosituasjon = listOf(bosituasjongrunnlagEnslig())),
                    vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                        uføre = innvilgetUførevilkår(),
                        utenlandsopphold = utlandsoppholdInnvilget()
                    ),
                ),
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    fixedClock
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAvslåttFlyktning(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentAvslagBeregning.copy(
                    grunnlagsdata = Grunnlagsdata.create(bosituasjon = listOf(bosituasjongrunnlagEnslig())),
                    vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                        uføre = innvilgetUførevilkår(),
                        utenlandsopphold = utlandsoppholdInnvilget()
                    ),
                ),
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    fixedClock
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentAvslagBeregning.copy(
                    grunnlagsdata = Grunnlagsdata.create(bosituasjon = listOf(bosituasjongrunnlagEnslig())),
                    vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                        uføre = innvilgetUførevilkår(),
                    ),
                ),
                Statusovergang.TilVilkårsvurdert(
                    Behandlingsinformasjon().withAvslåttFlyktning(),
                    fixedClock,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `ulovlige statusoverganger`() {
            listOf(
                tilAttesteringInnvilget,
                tilAttesteringAvslagVilkår,
                tilAttesteringAvslagBeregning,
                iverksattInnvilget,
                iverksattAvslagVilkår,
                iverksattAvslagBeregning,
                lukketSøknadsbehandling,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilVilkårsvurdert(
                            Behandlingsinformasjon().withAlleVilkårOppfylt(),
                            fixedClock
                        ),
                    )
                }
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilVilkårsvurdert(
                            Behandlingsinformasjon().withAvslåttFlyktning(),
                            fixedClock,
                        ),
                    )
                }
            }
        }
    }

    @Nested
    inner class Beregnet {
        @Test
        fun `vilkårsvurdert innvilget til beregnet innvilget`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget
        }

        @Test
        fun `vilkårsvurdert innvilget til beregnet avslag`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag
        }

        @Test
        fun `beregnet innvilget til beregnet innvilget`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget
        }

        @Test
        fun `beregnet innvilget til beregnet avslag`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag
        }

        @Test
        fun `beregnet avslag til beregnet innvilget`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget
        }

        @Test
        fun `beregnet avslag til beregnet avslag`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag
        }

        @Test
        fun `simulert til beregnet innvilget`() {
            statusovergang(
                simulert,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget
        }

        @Test
        fun `simulert til beregnet avslag`() {
            statusovergang(
                simulert,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag
        }

        @Test
        fun `underkjent avslag med beregning til beregnet innvilget`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget.medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev)
                .copy(attesteringer = Attesteringshistorikk.create(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent avslag med beregning til beregnet avslag`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag.medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev)
                .copy(attesteringer = Attesteringshistorikk.create(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent innvilget til beregnet innvilget`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget.medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev)
                .copy(attesteringer = Attesteringshistorikk.create(listOf(underkjentInnvilget.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent innvilget til beregnet avslag`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag.medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev)
                .copy(attesteringer = Attesteringshistorikk.create(listOf(underkjentInnvilget.attesteringer.hentSisteAttestering())))
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
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilBeregnet { innvilgetBeregning },
                    )
                }
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilBeregnet { avslagBeregning },
                    )
                }
            }
        }
    }

    @Nested
    inner class TilSimulert {
        @Test
        fun `beregnet innvilget til kunne ikke simulere`() {
            forsøkStatusovergang(
                beregnetInnvilget,
                Statusovergang.TilSimulert {
                    SimuleringFeilet.TEKNISK_FEIL.left()
                },
            ) shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
        }

        @Test
        fun `beregnet innvilget til simulering`() {
            forsøkStatusovergang(
                beregnetInnvilget,
                Statusovergang.TilSimulert {
                    simulering.right()
                },
            ) shouldBe simulert.right()
        }

        @Test
        fun `simulering til kunne ikke simulere`() {
            forsøkStatusovergang(
                simulert,
                Statusovergang.TilSimulert {
                    SimuleringFeilet.TEKNISK_FEIL.left()
                },
            ) shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
        }

        @Test
        fun `simulering til simulering`() {
            forsøkStatusovergang(
                simulert,
                Statusovergang.TilSimulert {
                    simulering.right()
                },
            ) shouldBe simulert.right()
        }

        @Test
        fun `underkjent innvilgning  til kunne ikke simulere`() {
            forsøkStatusovergang(
                underkjentInnvilget,
                Statusovergang.TilSimulert {
                    SimuleringFeilet.TEKNISK_FEIL.left()
                },
            ) shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
        }

        @Test
        fun `underkjent innvilgning til simulering`() {
            forsøkStatusovergang(
                underkjentInnvilget,
                Statusovergang.TilSimulert {
                    simulering.right()
                },
            ) shouldBe simulert.copy(
                fritekstTilBrev = "Fritekst til brev",
                attesteringer = Attesteringshistorikk.create(listOf(underkjentInnvilget.attesteringer.hentSisteAttestering())),
            )
                .right()
        }

        @Test
        fun `ulovlige overganger`() {
            listOf(
                opprettet,
                vilkårsvurdertAvslag,
                vilkårsvurdertInnvilget,
                beregnetAvslag,
                tilAttesteringAvslagBeregning,
                tilAttesteringAvslagVilkår,
                tilAttesteringInnvilget,
                underkjentAvslagBeregning,
                underkjentAvslagVilkår,
                iverksattAvslagBeregning,
                iverksattAvslagVilkår,
                iverksattInnvilget,
                lukketSøknadsbehandling,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    forsøkStatusovergang(
                        it,
                        Statusovergang.TilSimulert { simulering.right() },
                    )
                }
            }
        }
    }

    @Nested
    inner class TilAttestering {
        @Test
        fun `vilkårsvurder avslag til attestering`() {
            statusovergang(
                vilkårsvurdertAvslag,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringAvslagVilkår
        }

        @Test
        fun `vilkårsvurder beregning til attestering`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringAvslagBeregning
        }

        @Test
        fun `simulert til attestering`() {
            statusovergang(
                simulert,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringInnvilget
        }

        @Test
        fun `underkjent avslag vilkår til attestering`() {
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringAvslagVilkår.copy(
                attesteringer = Attesteringshistorikk.create(
                    listOf(
                        underkjentAvslagVilkår.attesteringer.hentSisteAttestering(),
                    ),
                ),
            )
        }

        @Test
        fun `underkjent avslag beregning til attestering`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringAvslagBeregning.copy(
                attesteringer = Attesteringshistorikk.create(
                    listOf(
                        underkjentAvslagBeregning.attesteringer.hentSisteAttestering(),
                    ),
                ),
            )
        }

        @Test
        fun `underkjent innvilging til attestering`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringInnvilget.copy(attesteringer = Attesteringshistorikk.create(listOf(underkjentInnvilget.attesteringer.hentSisteAttestering())))
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
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
                    )
                }
            }
        }
    }

    @Nested
    inner class TilUnderkjent {
        @Test
        fun `til attestering avslag vilkår til underkjent avslag vilkår`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår,
                Statusovergang.TilUnderkjent(attesteringUnderkjent(clock = fixedClock)),
            ) shouldBe underkjentAvslagVilkår.right()
        }

        @Test
        fun `til attestering avslag beregning til underkjent avslag beregning`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning,
                Statusovergang.TilUnderkjent(attesteringUnderkjent(clock = fixedClock)),
            ) shouldBe underkjentAvslagBeregning.right()
        }

        @Test
        fun `til attestering innvilget til underkjent innvilging`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget,
                Statusovergang.TilUnderkjent(attesteringUnderkjent(clock = fixedClock)),
            ) shouldBe underkjentInnvilget.right()
        }

        @Test
        fun `til attestering avslag vilkår kan ikke underkjenne sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår.copy(saksbehandler = NavIdentBruker.Saksbehandler("sneaky")),
                Statusovergang.TilUnderkjent(
                    Attestering.Underkjent(
                        attestant = NavIdentBruker.Attestant("sneaky"),
                        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        kommentar = "",
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ) shouldBe Statusovergang.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `til attestering avslag beregning kan ikke underkjenne sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning.copy(saksbehandler = NavIdentBruker.Saksbehandler("sneaky")),
                Statusovergang.TilUnderkjent(
                    Attestering.Underkjent(
                        attestant = NavIdentBruker.Attestant("sneaky"),
                        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        kommentar = "",
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ) shouldBe Statusovergang.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `til attestering innvilget kan ikke underkjenne sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget.copy(saksbehandler = NavIdentBruker.Saksbehandler("sneaky")),
                Statusovergang.TilUnderkjent(
                    Attestering.Underkjent(
                        attestant = NavIdentBruker.Attestant("sneaky"),
                        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        kommentar = "",
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ) shouldBe Statusovergang.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `ulovlige overganger`() {
            listOf(
                opprettet,
                vilkårsvurdertInnvilget,
                vilkårsvurdertAvslag,
                beregnetInnvilget,
                beregnetAvslag,
                simulert,
                underkjentAvslagVilkår,
                underkjentAvslagBeregning,
                underkjentInnvilget,
                iverksattAvslagBeregning,
                iverksattAvslagVilkår,
                iverksattInnvilget,
                lukketSøknadsbehandling,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    forsøkStatusovergang(
                        it,
                        Statusovergang.TilUnderkjent(attesteringUnderkjent(clock = fixedClock)),
                    )
                }
            }
        }
    }

    @Nested
    inner class TilIverksatt {
        @Test
        fun `attestert avslag vilkår til iverksatt avslag vilkår`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår,
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { throw IllegalStateException() },
            ) shouldBe iverksattAvslagVilkår.right()
        }

        @Test
        fun `attestert avslag beregning til iverksatt avslag beregning`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning,
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { throw IllegalStateException() },
            ) shouldBe iverksattAvslagBeregning.right()
        }

        @Test
        fun `attestert innvilget til iverksatt innvilging`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget,
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { utbetalingId.right() },
            ) shouldBe iverksattInnvilget.right()
        }

        @Test
        fun `attestert avslag vilkår, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { throw IllegalStateException() },
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `attestert avslag beregning, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { throw IllegalStateException() },
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `attestert innvilget, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { UUID30.randomUUID().right() },
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `ulovlige overganger`() {
            listOf(
                opprettet,
                vilkårsvurdertInnvilget,
                vilkårsvurdertAvslag,
                beregnetInnvilget,
                beregnetAvslag,
                simulert,
                underkjentAvslagVilkår,
                underkjentAvslagBeregning,
                underkjentInnvilget,
                iverksattAvslagBeregning,
                iverksattAvslagVilkår,
                iverksattInnvilget,
                lukketSøknadsbehandling,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    forsøkStatusovergang(
                        it,
                        Statusovergang.TilUnderkjent(attesteringUnderkjent(clock = fixedClock)),
                    )
                }
            }
        }
    }

    @Nested
    inner class OppdaterStønadsperiode {
        @Test
        fun `lovlige overganger`() {
            listOf(
                opprettet,
                vilkårsvurdertInnvilget,
                vilkårsvurdertAvslag,
                beregnetInnvilget,
                beregnetAvslag,
                simulert,
                underkjentAvslagVilkår,
                underkjentAvslagBeregning,
                underkjentInnvilget,
            ).forEach {
                assertDoesNotThrow {
                    forsøkStatusovergang(
                        søknadsbehandling = it,
                        statusovergang = Statusovergang.OppdaterStønadsperiode(stønadsperiode, sak, fixedClock),
                    )
                }
            }
        }

        @Test
        fun `ulovlige overganger`() {
            listOf(
                tilAttesteringAvslagBeregning,
                tilAttesteringAvslagVilkår,
                tilAttesteringInnvilget,
                iverksattAvslagBeregning,
                iverksattAvslagVilkår,
                iverksattInnvilget,
                lukketSøknadsbehandling,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    forsøkStatusovergang(
                        søknadsbehandling = it,
                        statusovergang = Statusovergang.OppdaterStønadsperiode(
                            oppdatertStønadsperiode = stønadsperiode,
                            sak = sak,
                            clock = fixedClock,
                        ),
                    )
                }
            }
        }

        @Test
        fun `oppdaterer perioden riktig`() {
            val (sak, vilkårsvurdert) = søknadsbehandlingVilkårsvurdertInnvilget()

            val nyPeriode = Periode.create(1.februar(2022), 31.mars(2022))
            val actual = forsøkStatusovergang(
                søknadsbehandling = vilkårsvurdert,
                statusovergang = Statusovergang.OppdaterStønadsperiode(
                    oppdatertStønadsperiode = Stønadsperiode.create(nyPeriode, ""),
                    sak = sak,
                    clock = fixedClock,
                ),
            )
            actual.orNull()!!.periode shouldBe nyPeriode
            actual.orNull()!!.vilkårsvurderinger.uføre.grunnlag.first().periode shouldBe nyPeriode
            actual.orNull()!!.vilkårsvurderinger.formue.grunnlag.first().periode shouldBe nyPeriode
            actual.orNull()!!.grunnlagsdata.bosituasjon.first().periode shouldBe nyPeriode
        }

        @Test
        fun `stønadsperioder skal ikke kunne overlappe`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                    begrunnelse = "kek",
                ),
            )
            val opprettetSøknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second

            val nyPeriode = Periode.create(1.desember(2021), 31.mars(2022))

            val actual = forsøkStatusovergang(
                søknadsbehandling = opprettetSøknadsbehandling,
                statusovergang = Statusovergang.OppdaterStønadsperiode(
                    oppdatertStønadsperiode = Stønadsperiode.create(nyPeriode, ""),
                    sak = sak,
                    clock = fixedClock,
                ),
            )
            actual shouldBe Statusovergang.OppdaterStønadsperiode.KunneIkkeOppdatereStønadsperiode.StønadsperiodeOverlapperMedLøpendeStønadsperiode.left()
        }

        @Test
        fun `stønadsperioder skal ikke kunne legges forut for eksisterende stønadsperioder`() {
            val (sak, stønadsperiode1) = vedtakSøknadsbehandlingIverksattInnvilget()

            val (_, stønadsperiode2) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = Periode.create(1.januar(2023), 31.desember(2023)),
                    begrunnelse = "ny periode da vett",
                ),
            )

            val mellomToAndrePerioder = søknadsbehandlingVilkårsvurdertUavklart().second

            val nyPeriode = Stønadsperiode.create(
                periode = Periode.create(1.januar(2022), 31.desember(2022)),
                begrunnelse = "ny periode da vett",
            )

            val sakMedBehandlingOgVedtak = sak.copy(
                søknadsbehandlinger = listOf(
                    stønadsperiode1.behandling,
                    stønadsperiode2.behandling,
                    mellomToAndrePerioder,
                ),
                vedtakListe = listOf(stønadsperiode1, stønadsperiode2),
            )

            forsøkStatusovergang(
                søknadsbehandling = mellomToAndrePerioder,
                statusovergang = Statusovergang.OppdaterStønadsperiode(
                    oppdatertStønadsperiode = nyPeriode,
                    sak = sakMedBehandlingOgVedtak,
                    clock = fixedClock,
                ),
            ).let {
                it shouldBe Statusovergang.OppdaterStønadsperiode.KunneIkkeOppdatereStønadsperiode.StønadsperiodeForSenerePeriodeEksisterer.left()
            }
        }
    }
}
