package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.beregnetSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingUføre
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulerNyUtbetaling
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulertSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadId
import no.nav.su.se.bakover.test.søknadsbehandlingSimulert
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.underkjentSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.vilkår.avslåttFormueVilkår
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårVurdertTilUavklart
import no.nav.su.se.bakover.test.vilkår.innvilgetFormueVilkår
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vilkår.utilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt12000
import no.nav.su.se.bakover.test.vilkårsvurderingerSøknadsbehandlingInnvilget
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingUføre
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class StatusovergangTest {

    private val stønadsperiode = stønadsperiode2021

    private val sakOgUavklart = søknadsbehandlingVilkårsvurdertUavklart(
        stønadsperiode = stønadsperiode,
    )

    private val opprettet = sakOgUavklart.second

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
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail() as Søknadsbehandling.Beregnet.Innvilget

    private val beregnetAvslag: Søknadsbehandling.Beregnet.Avslag =
        vilkårsvurdertInnvilget.leggTilUførevilkår(
            uførhet = innvilgetUførevilkår(forventetInntekt = 11000000),
        ).getOrFail().beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail() as Søknadsbehandling.Beregnet.Avslag

    private val simulert: Søknadsbehandling.Simulert =
        beregnetInnvilget.simuler(
            saksbehandler = saksbehandler,
            simuler = {
                simulerNyUtbetaling(
                    sak = sakOgUavklart.first,
                    request = it,
                    clock = fixedClock,
                )
            },
        ).getOrFail()

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
        underkjentInnvilget.lukkSøknadsbehandlingOgSøknad(
            trekkSøknad(søknadId),
        ).getOrFail()

    @Nested
    inner class TilVilkårsvurdert {
        // @Test
        // fun `opprettet til vilkårsvurdert innvilget`() {
        //     statusovergang(
        //         opprettet.copy(
        //             grunnlagsdata = vilkårsvurdertInnvilget.grunnlagsdata,
        //             vilkårsvurderinger = vilkårsvurdertInnvilget.vilkårsvurderinger,
        //         ),
        //     ).let { (_, uavklart) ->
        //         uavklart.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Uavklart>().also {
        //             it.leggTilOpplysningspliktVilkår(tilstrekkeligDokumentert(), fixedClock).getOrFail()
        //                 .shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        //         }
        //     }
        //
        // }
        //
        @Test
        fun `opprettet til vilkårsvurdert avslag`() {
            nySøknadsbehandlingUføre().also { (_, ny) ->
                ny.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Uavklart>().also {
                    it.leggTilInstitusjonsoppholdVilkår(
                        vilkår = institusjonsoppholdvilkårAvslag(),
                    ).getOrFail().also {
                        it.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
                    }
                }
            }
        }

        @Test
        fun `opprettet til vilkårsvurdert uavklart (opprettet)`() {
            nySøknadsbehandlingUføre().also { (_, ny) ->
                ny.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Uavklart>().also {
                    it.leggTilFastOppholdINorgeVilkår(fastOppholdVilkårVurdertTilUavklart()).getOrFail().also {
                        it.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Uavklart>()
                    }
                }
            }
        }

        @Test
        fun `håndtering av opplysningsplikt`() {
            nySøknadsbehandlingUføre().also { (_, uavklart) ->
                uavklart.leggTilUførevilkår(innvilgetUførevilkår()).getOrFail().also { vilkårsvurdert ->
                    vilkårsvurdert.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Uavklart>()
                    vilkårsvurdert.vilkårsvurderinger.uføreVilkår().getOrFail().shouldBeType<UføreVilkår.Vurdert>()
                    // skal legges til implisitt hvis det ikke er vurdert fra før
                    vilkårsvurdert.vilkårsvurderinger.opplysningspliktVilkår()
                        .shouldBeType<OpplysningspliktVilkår.Vurdert>()
                }
            }

            vilkårsvurdertSøknadsbehandlingUføre(
                customVilkår = listOf(utilstrekkeligDokumentert()),
            ).also { (_, uavklart) ->
                uavklart.leggTilUførevilkår(innvilgetUførevilkår()).getOrFail().also { vilkårsvurdert ->
                    vilkårsvurdert.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
                    vilkårsvurdert.vilkårsvurderinger.uføreVilkår().getOrFail().shouldBeType<UføreVilkår.Vurdert>()
                    // skal ikke legges til implisitt ved oppdatering av andre vilkår da dette allerede er vurdert
                    vilkårsvurdert.vilkårsvurderinger.opplysningspliktVilkår()
                        .shouldBeType<OpplysningspliktVilkår.Vurdert>()
                    vilkårsvurdert.vilkårsvurderinger.vurdering shouldBe Vilkårsvurderingsresultat.Avslag(
                        setOf(
                            vilkårsvurdert.vilkårsvurderinger.opplysningspliktVilkår(),
                        ),
                    )
                }
            }
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert innvilget`() {
            vilkårsvurdertSøknadsbehandlingUføre().also { (_, innvilget) ->
                innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>().also {
                    it.leggTilFormuevilkår(innvilgetFormueVilkår()).getOrFail().also {
                        it.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
                    }
                }
            }
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert avslag`() {
            vilkårsvurdertSøknadsbehandlingUføre().also { (_, innvilget) ->
                innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>().also {
                    it.leggTilInstitusjonsoppholdVilkår(institusjonsoppholdvilkårAvslag()).getOrFail().also { avslag ->
                        avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
                    }
                }
            }

            vilkårsvurdertSøknadsbehandlingUføre().also { (_, innvilget) ->
                innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>().also {
                    it.leggTilOpplysningspliktVilkår(utilstrekkeligDokumentert()).getOrFail()
                        .also { avslag ->
                            avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
                        }
                }
            }
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert innvilget`() {
            vilkårsvurdertSøknadsbehandlingUføre(
                customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            ).also { (_, avslag) ->
                avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>().also {
                    it.leggTilInstitusjonsoppholdVilkår(institusjonsoppholdvilkårInnvilget()).getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
                        }
                }
            }

            vilkårsvurdertSøknadsbehandlingUføre(customVilkår = listOf(avslåttUførevilkårUtenGrunnlag())).also { (_, avslag) ->
                avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>().also {
                    it.leggTilUførevilkår(innvilgetUførevilkår()).getOrFail().also { innvilget ->
                        innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
                    }
                }
            }
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert avslag`() {
            vilkårsvurdertSøknadsbehandlingUføre(customVilkår = listOf(avslåttUførevilkårUtenGrunnlag())).also { (_, avslag) ->
                avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>().also {
                    it.leggTilFormuevilkår(avslåttFormueVilkår()).getOrFail().also { avslag ->
                        avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
                    }
                }
            }
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert innvilget`() {
            beregnetSøknadsbehandlingUføre().also { (_, beregnet) ->
                beregnet.shouldBeType<Søknadsbehandling.Beregnet.Innvilget>().also {
                    beregnet.leggTilInstitusjonsoppholdVilkår(institusjonsoppholdvilkårInnvilget()).getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
                        }
                }
            }

            beregnetSøknadsbehandlingUføre().also { (_, beregnet) ->
                beregnet.shouldBeType<Søknadsbehandling.Beregnet.Innvilget>().also {
                    beregnet.leggTilUtenlandsopphold(utenlandsoppholdInnvilget()).getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
                        }
                }
            }
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert avslag`() {
            beregnetSøknadsbehandlingUføre().also { (_, beregnet) ->
                beregnet.shouldBeType<Søknadsbehandling.Beregnet.Innvilget>().also {
                    beregnet.leggTilUtenlandsopphold(utenlandsoppholdAvslag()).getOrFail().also { avslag ->
                        avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
                    }
                }
            }
        }

        @Test
        fun `beregnet avslag til vilkårsvurdert innvilget`() {
            beregnetSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        arbeidsinntekt = 50000.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).also { (_, beregnet) ->
                beregnet.shouldBeType<Søknadsbehandling.Beregnet.Avslag>().also {
                    beregnet.leggTilUtenlandsopphold(utenlandsoppholdInnvilget()).getOrFail()
                        .also { innvilget -> innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>() }
                }
            }
        }

        @Test
        fun `beregnet avslag til vilkårsvurdert avslag`() {
            beregnetSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        arbeidsinntekt = 50000.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).also { (_, beregnet) ->
                beregnet.shouldBeType<Søknadsbehandling.Beregnet.Avslag>().also {
                    beregnet.leggTilInstitusjonsoppholdVilkår(institusjonsoppholdvilkårAvslag()).getOrFail()
                        .also { innvilget -> innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>() }
                }
            }
        }

        @Test
        fun `simulert til vilkårsvurdert innvilget`() {
            simulertSøknadsbehandlingUføre().also { (_, simulert) ->
                simulert.shouldBeType<Søknadsbehandling.Simulert>().also {
                    simulert.leggTilOpplysningspliktVilkår(tilstrekkeligDokumentert()).getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
                        }
                }
            }
        }

        @Test
        fun `simulert til vilkårsvurdert avslag`() {
            simulertSøknadsbehandlingUføre().also { (_, simulert) ->
                simulert.shouldBeType<Søknadsbehandling.Simulert>().also {
                    simulert.leggTilOpplysningspliktVilkår(utilstrekkeligDokumentert()).getOrFail()
                        .also { avslag ->
                            avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
                        }
                }
            }
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert innvilget`() {
            underkjentSøknadsbehandlingUføre().also { (_, underkjent) ->
                underkjent.shouldBeType<Søknadsbehandling.Underkjent.Innvilget>().also {
                    underkjent.leggTilOpplysningspliktVilkår(tilstrekkeligDokumentert()).getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
                        }
                }
            }
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert avslag`() {
            underkjentSøknadsbehandlingUføre().also { (_, underkjent) ->
                underkjent.shouldBeType<Søknadsbehandling.Underkjent.Innvilget>().also {
                    underkjent.leggTilInstitusjonsoppholdVilkår(
                        institusjonsoppholdvilkårAvslag(),
                    ).getOrFail().also { avslag ->
                        avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
                    }
                }
            }
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert innvilget`() {
            underkjentSøknadsbehandlingUføre(
                customVilkår = listOf(avslåttUførevilkårUtenGrunnlag()),
            ).also { (_, underkjent) ->
                underkjent.shouldBeType<Søknadsbehandling.Underkjent.Avslag.UtenBeregning>().also {
                    underkjent.leggTilUførevilkår(innvilgetUførevilkår()).getOrFail().also { innvilget ->
                        innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
                    }
                }
            }
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert avslag`() {
            underkjentSøknadsbehandlingUføre(
                customVilkår = listOf(avslåttUførevilkårUtenGrunnlag()),
            ).also { (_, underkjent) ->
                underkjent.shouldBeType<Søknadsbehandling.Underkjent.Avslag.UtenBeregning>().also {
                    underkjent.leggTilUførevilkår(avslåttUførevilkårUtenGrunnlag()).getOrFail()
                        .also { avslag ->
                            avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
                        }
                }
            }
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert innvilget`() {
            underkjentSøknadsbehandlingUføre(
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
            ).also { (_, underkjent) ->
                underkjent.shouldBeType<Søknadsbehandling.Underkjent.Avslag.MedBeregning>().also {
                    underkjent.leggTilFradragsgrunnlag(emptyList()).getOrFail().also { innvilget ->
                        innvilget.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
                    }
                }
            }
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert avslag`() {
            underkjentSøknadsbehandlingUføre(
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
            ).also { (_, underkjent) ->
                underkjent.shouldBeType<Søknadsbehandling.Underkjent.Avslag.MedBeregning>().also {
                    underkjent.leggTilInstitusjonsoppholdVilkår(
                        institusjonsoppholdvilkårAvslag(),
                    ).getOrFail().also { avslag ->
                        avslag.shouldBeType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
                    }
                }
            }
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
                it.leggTilFastOppholdINorgeVilkår(fastOppholdVilkårInnvilget())
                    .shouldBeType<Either.Left<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår.UgyldigTilstand>>()
                it.leggTilFormuevilkår(avslåttFormueVilkår())
                    .shouldBeType<Either.Left<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.UgyldigTilstand>>()
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
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail() shouldBe beregnetInnvilget
        }

        @Test
        fun `kan beregne på nytt for beregnet innvilget`() {
            beregnetInnvilget.beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail() shouldBe beregnetInnvilget
        }

        @Test
        fun `kan beregne på nytt for beregnet avslag`() {
            beregnetAvslag.beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail() shouldBe beregnetAvslag
        }

        @Test
        fun `kan beregne på nytt for simulert`() {
            simulert.beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail() shouldBe beregnetInnvilget
        }

        @Test
        fun `kan beregne på nytt underkjent avslag med beregning`() {
            underkjentAvslagBeregning.beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail() shouldBe beregnetAvslag
                .medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev)
                .copy(attesteringer = Attesteringshistorikk.create(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `kan beregne på nytt underkjent innvilgelse med beregning`() {
            underkjentInnvilget.beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
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
                    satsFactory = satsFactoryTestPåDato(),
                ) shouldBe KunneIkkeBeregne.UgyldigTilstand(it::class).left()
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
                ),
            ) shouldBe iverksattAvslagVilkår.right()
        }

        @Test
        fun `attestert avslag beregning til iverksatt avslag beregning`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning,
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                    hentOpprinneligAvkorting = { null },
                ),
            ) shouldBe iverksattAvslagBeregning.right()
        }

        @Test
        fun `attestert innvilget til iverksatt innvilging`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget,
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                    hentOpprinneligAvkorting = { null },
                ),
            ) shouldBe iverksattInnvilget.right()
        }

        @Test
        fun `attestert avslag vilkår, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                    hentOpprinneligAvkorting = { null },
                ),
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `attestert avslag beregning, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                    hentOpprinneligAvkorting = { null },
                ),
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `attestert innvilget, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                    hentOpprinneligAvkorting = { null },
                ),
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
                    ),
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
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(),
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
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                ).isLeft() shouldBe true
            }
        }
    }
}
