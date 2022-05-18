package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withAvslåttFlyktning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTest
import no.nav.su.se.bakover.test.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.innvilgetUførevilkår
import no.nav.su.se.bakover.test.innvilgetUførevilkårForventetInntekt12000
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadsbehandlingSimulert
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.utenlandsoppholdInnvilget
import no.nav.su.se.bakover.test.utilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkårsvurderingerSøknadsbehandlingInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class StatusovergangTest {

    private val stønadsperiode = stønadsperiode2021

    private val sakOgUavklart = søknadsbehandlingVilkårsvurdertUavklart(
        stønadsperiode = stønadsperiode,
    )

    private val opprettet = sakOgUavklart.second

    private val simulering = no.nav.su.se.bakover.test.simuleringNy()

    private val attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant"), fixedTidspunkt)

    private val fritekstTilBrev: String = "Fritekst til brev"

    private val vilkårsvurdertInnvilget: Søknadsbehandling.Vilkårsvurdert.Innvilget =
        søknadsbehandlingVilkårsvurdertInnvilget(
            vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget(
                uføre = innvilgetUførevilkårForventetInntekt12000(),
            ),
        ).second

    private val vilkårsvurdertAvslag: Søknadsbehandling.Vilkårsvurdert.Avslag =
        søknadsbehandlingVilkårsvurdertAvslag().second

    private val beregnetInnvilget: Søknadsbehandling.Beregnet.Innvilget =
        vilkårsvurdertInnvilget.beregn(
            begrunnelse = null,
            clock = fixedClock,
            formuegrenserFactory = formuegrenserFactoryTest,
            satsFactory = satsFactoryTest,
        ).getOrFail() as Søknadsbehandling.Beregnet.Innvilget

    private val beregnetAvslag: Søknadsbehandling.Beregnet.Avslag =
        vilkårsvurdertInnvilget.leggTilUførevilkår(
            uførhet = innvilgetUførevilkår(forventetInntekt = 11000000),
            clock = fixedClock,
            formuegrenserFactory = formuegrenserFactoryTest,
        ).getOrFail().beregn(
            begrunnelse = null,
            clock = fixedClock,
            formuegrenserFactory = formuegrenserFactoryTest,
            satsFactory = satsFactoryTest,
        ).getOrFail() as Søknadsbehandling.Beregnet.Avslag

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
                    behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
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
                    behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `opprettet til vilkårsvurdert uavklart (opprettet)`() {
            statusovergang(
                opprettet,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe opprettet.copy(
                vilkårsvurderinger = opprettet.vilkårsvurderinger.copy(
                    // legges til automatisk
                    opplysningsplikt = tilstrekkeligDokumentert(),
                ),
            )
        }

        @Test
        fun `vurdert opplysningsplikt forblir vurdert`() {
            statusovergang(
                opprettet.leggTilOpplysningspliktVilkår(
                    opplysningspliktVilkår = utilstrekkeligDokumentert(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ).getOrFail(),
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ).shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
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
                        utenlandsopphold = utenlandsoppholdInnvilget(),
                        formue = formuevilkårIkkeVurdert(),
                        opplysningsplikt = tilstrekkeligDokumentert(),
                    ),
                ),
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ).shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert avslag`() {
            statusovergang(
                vilkårsvurdertAvslag,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `beregnet avslag til vilkårsvurdert innvilget`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `beregnet avslag til vilkårsvurdert avslag`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `simulert til vilkårsvurdert innvilget`() {
            statusovergang(
                simulert,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `simulert til vilkårsvurdert avslag`() {
            statusovergang(
                simulert,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ) shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
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
                        utenlandsopphold = utenlandsoppholdInnvilget(),
                        formue = formuevilkårIkkeVurdert(),
                        opplysningsplikt = tilstrekkeligDokumentert(),
                    ),
                ),
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ).shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
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
                        utenlandsopphold = utenlandsoppholdInnvilget(),
                        formue = formuevilkårIkkeVurdert(),
                        opplysningsplikt = tilstrekkeligDokumentert(),
                    ),
                ),
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ),
            ).shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentAvslagBeregning.copy(
                    grunnlagsdata = Grunnlagsdata.create(bosituasjon = listOf(bosituasjongrunnlagEnslig())),
                    vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                        uføre = innvilgetUførevilkår(),
                        formue = formuevilkårIkkeVurdert(),
                    ),
                ),
                Statusovergang.TilVilkårsvurdert(
                    behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
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
                            behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt(),
                            clock = fixedClock,
                            formuegrenserFactory = formuegrenserFactoryTest,
                        ),
                    )
                }
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilVilkårsvurdert(
                            behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                            clock = fixedClock,
                            formuegrenserFactory = formuegrenserFactoryTest,
                        ),
                    )
                }
            }
        }
    }

    @Nested
    inner class Beregnet {
        @Test
        fun `kan beregne for vilkårsvurdert innvilget`() {
            vilkårsvurdertInnvilget.beregn(
                begrunnelse = null,
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTest,
                satsFactory = satsFactoryTest,
            ).getOrFail() shouldBe beregnetInnvilget
        }

        @Test
        fun `kan beregne på nytt for beregnet innvilget`() {
            beregnetInnvilget.beregn(
                begrunnelse = null,
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTest,
                satsFactory = satsFactoryTest,
            ).getOrFail() shouldBe beregnetInnvilget
        }

        @Test
        fun `kan beregne på nytt for beregnet avslag`() {
            beregnetAvslag.beregn(
                begrunnelse = null,
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTest,
                satsFactory = satsFactoryTest,
            ).getOrFail() shouldBe beregnetAvslag
        }

        @Test
        fun `kan beregne på nytt for simulert`() {
            simulert.beregn(
                begrunnelse = null,
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTest,
                satsFactory = satsFactoryTest,
            ).getOrFail() shouldBe beregnetInnvilget
        }

        @Test
        fun `kan beregne på nytt underkjent avslag med beregning`() {
            underkjentAvslagBeregning.beregn(
                begrunnelse = null,
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTest,
                satsFactory = satsFactoryTest,
            ).getOrFail() shouldBe beregnetAvslag
                .medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev)
                .copy(attesteringer = Attesteringshistorikk.create(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `kan beregne på nytt underkjent innvilgelse med beregning`() {
            underkjentInnvilget.beregn(
                begrunnelse = null,
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTest,
                satsFactory = satsFactoryTest,
            ).getOrFail() shouldBe beregnetInnvilget
                .medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev)
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
                it.beregn(
                    begrunnelse = null,
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                    satsFactory = satsFactoryTest,
                ) shouldBe Søknadsbehandling.KunneIkkeBeregne.UgyldigTilstand(it::class).left()
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
            ) shouldBe tilAttesteringInnvilget.copy(
                attesteringer = Attesteringshistorikk.create(
                    listOf(
                        underkjentInnvilget.attesteringer.hentSisteAttestering(),
                    ),
                ),
            )
        }

        @Test
        fun `kaster excepiton hvis innvilget simulering inneholder feilutbetalinger`() {
            val simulertMedFeilutbetaling = søknadsbehandlingSimulert().let { (_, søknadsbehandling) ->
                søknadsbehandling.copy(
                    simulering = simuleringFeilutbetaling(
                        søknadsbehandling.beregning.getMånedsberegninger().first().periode,
                    ),
                )
            }
            assertThrows<IllegalStateException> {
                statusovergang(
                    simulertMedFeilutbetaling,
                    Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
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
                statusovergang(
                    underkjentInnvilgetMedFeilutbetaling,
                    Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
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
                    hentOpprinneligAvkorting = { null },
                )
            ) shouldBe iverksattAvslagVilkår.right()
        }

        @Test
        fun `attestert avslag beregning til iverksatt avslag beregning`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning,
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                    hentOpprinneligAvkorting = { null },
                )
            ) shouldBe iverksattAvslagBeregning.right()
        }

        @Test
        fun `attestert innvilget til iverksatt innvilging`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget,
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                    hentOpprinneligAvkorting = { null },
                )
            ) shouldBe iverksattInnvilget.right()
        }

        @Test
        fun `attestert avslag vilkår, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                    hentOpprinneligAvkorting = { null },
                )
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `attestert avslag beregning, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                    hentOpprinneligAvkorting = { null },
                )
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `attestert innvilget, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                    hentOpprinneligAvkorting = { null },
                )
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `kaster exception dersom simulering inneholder feilutbetaling`() {
            val medFeilutbetaling = søknadsbehandlingTilAttesteringInnvilget().let { (_, tilAttestering) ->
                tilAttestering.copy(
                    simulering = simuleringFeilutbetaling(
                        tilAttestering.beregning.getMånedsberegninger().first().periode,
                    ),
                )
            }
            assertThrows<IllegalStateException> {
                forsøkStatusovergang(
                    medFeilutbetaling,
                    Statusovergang.TilIverksatt(
                        attestering = attestering,
                        hentOpprinneligAvkorting = { null },
                    )
                )
            }
        }

        // TODO avkorting test hentOpprinneligAvkorting...

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
                it.oppdaterStønadsperiode(
                    oppdatertStønadsperiode = stønadsperiode,
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ).isRight() shouldBe true
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
                it.oppdaterStønadsperiode(
                    oppdatertStønadsperiode = stønadsperiode,
                    clock = fixedClock,
                    formuegrenserFactory = formuegrenserFactoryTest,
                ).isLeft() shouldBe true
            }
        }
    }
}
