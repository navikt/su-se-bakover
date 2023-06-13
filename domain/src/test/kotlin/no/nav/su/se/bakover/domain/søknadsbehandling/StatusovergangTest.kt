package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import io.kotest.matchers.types.shouldNotBeTypeOf
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersinformasjon
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.beregnetSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.getOrFailAsType
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.nySøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySøknadsbehandlingshendelse
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulertSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknad.søknadId
import no.nav.su.se.bakover.test.søknadsbehandlingSimulert
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.underkjentSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårVurdertTilUavklart
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

    private val stønadsperiode get() = stønadsperiode2021

    private val sakOgUavklart
        get() = søknadsbehandlingVilkårsvurdertUavklart(
            stønadsperiode = stønadsperiode,
        )

    private val opprettet get() = sakOgUavklart.second

    private val fritekstTilBrev: String = "Fritekst til brev"

    private val vilkårsvurdertInnvilget: VilkårsvurdertSøknadsbehandling.Innvilget
        get() =
            søknadsbehandlingVilkårsvurdertInnvilget(
                vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget(
                    uføre = innvilgetUførevilkårForventetInntekt12000(),
                ),
            ).second

    private val vilkårsvurdertAvslag: VilkårsvurdertSøknadsbehandling.Avslag
        get() =
            søknadsbehandlingVilkårsvurdertAvslag().second

    private val beregnetInnvilget: BeregnetSøknadsbehandling.Innvilget
        get() =
            vilkårsvurdertInnvilget.beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
                nySaksbehandler = saksbehandler,
                uteståendeAvkortingPåSak = null,
            ).getOrFail() as BeregnetSøknadsbehandling.Innvilget

    private val beregnetAvslag: BeregnetSøknadsbehandling.Avslag
        get() =
            vilkårsvurdertInnvilget.leggTilUførevilkår(
                uførhet = innvilgetUførevilkår(forventetInntekt = 11000000),
                saksbehandler = saksbehandler,
                clock = fixedClock,
            ).getOrFailAsType<VilkårsvurdertSøknadsbehandling.Innvilget>().beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
                nySaksbehandler = saksbehandler,
                uteståendeAvkortingPåSak = null,
            ).getOrFail() as BeregnetSøknadsbehandling.Avslag

    private val simulert: SimulertSøknadsbehandling
        get() =
            beregnetInnvilget.simuler(
                saksbehandler = saksbehandler,
                clock = fixedClock,
            ) { _, _ ->
                simulerUtbetaling(
                    sak = sakOgUavklart.first,
                    søknadsbehandling = beregnetInnvilget,
                ).map {
                    it.simulering
                }
            }.getOrFail()

    private val tilAttesteringInnvilget: SøknadsbehandlingTilAttestering.Innvilget
        get() =
            simulert.tilAttestering(saksbehandler, fritekstTilBrev, fixedClock).getOrFail()

    private val tilAttesteringAvslagVilkår: SøknadsbehandlingTilAttestering.Avslag.UtenBeregning
        get() =
            vilkårsvurdertAvslag.tilAttesteringForSaksbehandler(saksbehandler, fritekstTilBrev, fixedClock).getOrFail()

    private val tilAttesteringAvslagBeregning: SøknadsbehandlingTilAttestering.Avslag.MedBeregning
        get() =
            beregnetAvslag.tilAttestering(saksbehandler, fritekstTilBrev, fixedClock).getOrFail()

    private val underkjentInnvilget: UnderkjentSøknadsbehandling.Innvilget
        get() =
            tilAttesteringInnvilget.tilUnderkjent(attesteringUnderkjent(clock = fixedClock))
    private val underkjentAvslagVilkår: UnderkjentSøknadsbehandling.Avslag.UtenBeregning
        get() =
            tilAttesteringAvslagVilkår.tilUnderkjent(attesteringUnderkjent(clock = fixedClock))
    private val underkjentAvslagBeregning: UnderkjentSøknadsbehandling.Avslag.MedBeregning
        get() =
            tilAttesteringAvslagBeregning.tilUnderkjent(attesteringUnderkjent(clock = fixedClock))
    private val iverksattInnvilget get() = iverksattSøknadsbehandling().second as IverksattSøknadsbehandling.Innvilget

    private val iverksattAvslagVilkår
        get() = iverksattSøknadsbehandling(
            customVilkår = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).second as IverksattSøknadsbehandling.Avslag.UtenBeregning

    private val iverksattAvslagBeregning
        get() = iverksattSøknadsbehandling(
            customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 30000.0)),
        ).second as IverksattSøknadsbehandling.Avslag.MedBeregning

    private val lukketSøknadsbehandling
        get() =
            underkjentInnvilget.lukkSøknadsbehandlingOgSøknad(
                trekkSøknad(søknadId),
            ).getOrFail()

    @Nested
    inner class TilVilkårsvurdert {

        @Test
        fun `opprettet til vilkårsvurdert avslag`() {
            nySøknadsbehandlingUføre().also { (_, ny) ->
                ny.shouldBeType<VilkårsvurdertSøknadsbehandling.Uavklart>().also {
                    it.leggTilInstitusjonsoppholdVilkår(
                        vilkår = institusjonsoppholdvilkårAvslag(),
                        saksbehandler = saksbehandler,
                        clock = fixedClock,
                    ).getOrFail().also {
                        it.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                    }
                }
            }
        }

        @Test
        fun `opprettet til vilkårsvurdert uavklart (opprettet)`() {
            nySøknadsbehandlingUføre().also { (_, ny) ->
                ny.shouldBeType<VilkårsvurdertSøknadsbehandling.Uavklart>().also {
                    it.leggTilFastOppholdINorgeVilkår(
                        saksbehandler = saksbehandler,
                        fastOppholdVilkårVurdertTilUavklart(),
                        clock = fixedClock,
                    ).getOrFail().also {
                        it.shouldBeType<VilkårsvurdertSøknadsbehandling.Uavklart>()
                    }
                }
            }
        }

        @Test
        fun `håndtering av opplysningsplikt`() {
            nySøknadsbehandlingUføre().also { (_, uavklart) ->
                uavklart.leggTilUførevilkår(
                    saksbehandler = saksbehandler,
                    innvilgetUførevilkår(),
                    clock = fixedClock,
                ).getOrFail()
                    .also { vilkårsvurdert ->
                        vilkårsvurdert.shouldBeType<VilkårsvurdertSøknadsbehandling.Uavklart>()
                        vilkårsvurdert.vilkårsvurderinger.uføreVilkår().getOrFail().shouldBeType<UføreVilkår.Vurdert>()
                        // skal legges til implisitt hvis det ikke er vurdert fra før
                        vilkårsvurdert.vilkårsvurderinger.opplysningspliktVilkår()
                            .shouldBeType<OpplysningspliktVilkår.Vurdert>()
                    }
            }

            vilkårsvurdertSøknadsbehandlingUføre(
                customVilkår = listOf(utilstrekkeligDokumentert()),
            ).also { (_, uavklart) ->
                uavklart.leggTilUførevilkår(
                    saksbehandler = saksbehandler,
                    innvilgetUførevilkår(),
                    clock = fixedClock,
                ).getOrFail()
                    .also { vilkårsvurdert ->
                        vilkårsvurdert.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
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
                innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>().also {
                    it.leggTilFormuegrunnlag(
                        request = LeggTilFormuevilkårRequest(
                            behandlingId = innvilget.id,
                            formuegrunnlag = nonEmptyListOf(
                                LeggTilFormuevilkårRequest.Grunnlag.Søknadsbehandling(
                                    periode = innvilget.periode,
                                    epsFormue = null,
                                    søkersFormue = Formuegrunnlag.Verdier.empty(),
                                    begrunnelse = null,
                                    måInnhenteMerInformasjon = false,
                                ),
                            ),
                            saksbehandler = saksbehandler,
                            tidspunkt = fixedTidspunkt,
                        ),
                        formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                    ).getOrFail().also {
                        it.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                    }
                }
            }
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert avslag`() {
            vilkårsvurdertSøknadsbehandlingUføre().also { (_, innvilget) ->
                innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>().also {
                    it.leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        institusjonsoppholdvilkårAvslag(),
                        clock = fixedClock,
                    ).getOrFail().also { avslag ->
                        avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                    }
                }
            }

            vilkårsvurdertSøknadsbehandlingUføre().also { (_, innvilget) ->
                innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>().also {
                    it.leggTilOpplysningspliktVilkår(
                        utilstrekkeligDokumentert(),
                    )
                        .getOrFail()
                        .also { avslag ->
                            avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                        }
                }
            }
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert innvilget`() {
            vilkårsvurdertSøknadsbehandlingUføre(
                customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            ).also { (_, avslag) ->
                avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>().also {
                    it.leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        institusjonsoppholdvilkårInnvilget(),
                        clock = fixedClock,
                    ).getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                        }
                }
            }

            vilkårsvurdertSøknadsbehandlingUføre(customVilkår = listOf(avslåttUførevilkårUtenGrunnlag())).also { (_, avslag) ->
                avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>().also {
                    it.leggTilUførevilkår(
                        saksbehandler = saksbehandler,
                        innvilgetUførevilkår(),
                        clock = fixedClock,
                    ).getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                        }
                }
            }
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert avslag`() {
            vilkårsvurdertSøknadsbehandlingUføre(customVilkår = listOf(avslåttUførevilkårUtenGrunnlag())).also { (_, avslag) ->
                avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>().also {
                    it.leggTilFormuegrunnlag(
                        request = LeggTilFormuevilkårRequest(
                            behandlingId = avslag.id,
                            formuegrunnlag = nonEmptyListOf(
                                LeggTilFormuevilkårRequest.Grunnlag.Søknadsbehandling(
                                    periode = avslag.periode,
                                    epsFormue = null,
                                    søkersFormue = Formuegrunnlag.Verdier.empty(),
                                    begrunnelse = null,
                                    måInnhenteMerInformasjon = false,
                                ),
                            ),
                            saksbehandler = saksbehandler,
                            tidspunkt = fixedTidspunkt,
                        ),
                        formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                    ).getOrFail()
                        .also { avslag ->
                            avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                        }
                }
            }
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert innvilget`() {
            beregnetSøknadsbehandlingUføre().also { (_, beregnet) ->
                beregnet.shouldBeType<BeregnetSøknadsbehandling.Innvilget>().also {
                    beregnet.leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        institusjonsoppholdvilkårInnvilget(),
                        clock = fixedClock,
                    ).getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                        }
                }
            }

            beregnetSøknadsbehandlingUføre().also { (_, beregnet) ->
                beregnet.shouldBeType<BeregnetSøknadsbehandling.Innvilget>().also {
                    beregnet.leggTilUtenlandsopphold(
                        saksbehandler = saksbehandler,
                        utenlandsoppholdInnvilget(),
                        clock = fixedClock,
                    )
                        .getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                        }
                }
            }
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert avslag`() {
            beregnetSøknadsbehandlingUføre().also { (_, beregnet) ->
                beregnet.shouldBeType<BeregnetSøknadsbehandling.Innvilget>().also {
                    beregnet.leggTilUtenlandsopphold(
                        saksbehandler = saksbehandler,
                        utenlandsoppholdAvslag(),
                        clock = fixedClock,
                    )
                        .getOrFail().also { avslag ->
                            avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
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
                beregnet.shouldBeType<BeregnetSøknadsbehandling.Avslag>().also {
                    beregnet.leggTilUtenlandsopphold(
                        saksbehandler = saksbehandler,
                        utenlandsoppholdInnvilget(),
                        clock = fixedClock,
                    )
                        .getOrFail()
                        .also { innvilget -> innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>() }
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
                beregnet.shouldBeType<BeregnetSøknadsbehandling.Avslag>().also {
                    beregnet.leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        institusjonsoppholdvilkårAvslag(),
                        clock = fixedClock,
                    ).getOrFail()
                        .also { innvilget -> innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>() }
                }
            }
        }

        @Test
        fun `simulert til vilkårsvurdert innvilget`() {
            simulertSøknadsbehandlingUføre().also { (_, simulert) ->
                simulert.shouldBeType<SimulertSøknadsbehandling>().also {
                    simulert.leggTilOpplysningspliktVilkår(
                        tilstrekkeligDokumentert(),
                    )
                        .getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                        }
                }
            }
        }

        @Test
        fun `simulert til vilkårsvurdert avslag`() {
            simulertSøknadsbehandlingUføre().also { (_, simulert) ->
                simulert.shouldBeType<SimulertSøknadsbehandling>().also {
                    simulert.leggTilOpplysningspliktVilkår(
                        utilstrekkeligDokumentert(),
                    )
                        .getOrFail()
                        .also { avslag ->
                            avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                        }
                }
            }
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert innvilget`() {
            underkjentSøknadsbehandlingUføre().also { (_, underkjent) ->
                underkjent.shouldBeType<UnderkjentSøknadsbehandling.Innvilget>().also {
                    underkjent.leggTilOpplysningspliktVilkår(
                        tilstrekkeligDokumentert(),
                    )
                        .getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                        }
                }
            }
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert avslag`() {
            underkjentSøknadsbehandlingUføre().also { (_, underkjent) ->
                underkjent.shouldBeType<UnderkjentSøknadsbehandling.Innvilget>().also {
                    underkjent.leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        institusjonsoppholdvilkårAvslag(),
                        clock = fixedClock,
                    ).getOrFail().also { avslag ->
                        avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                    }
                }
            }
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert innvilget`() {
            underkjentSøknadsbehandlingUføre(
                customVilkår = listOf(avslåttUførevilkårUtenGrunnlag()),
            ).also { (_, underkjent) ->
                underkjent.shouldBeType<UnderkjentSøknadsbehandling.Avslag.UtenBeregning>().also {
                    underkjent.leggTilUførevilkår(
                        saksbehandler = saksbehandler,
                        innvilgetUførevilkår(),
                        clock = fixedClock,
                    ).getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                        }
                }
            }
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert avslag`() {
            underkjentSøknadsbehandlingUføre(
                customVilkår = listOf(avslåttUførevilkårUtenGrunnlag()),
            ).also { (_, underkjent) ->
                underkjent.shouldBeType<UnderkjentSøknadsbehandling.Avslag.UtenBeregning>().also {
                    underkjent.leggTilUførevilkår(
                        saksbehandler = saksbehandler,
                        avslåttUførevilkårUtenGrunnlag(),
                        clock = fixedClock,
                    )
                        .getOrFail()
                        .also { avslag ->
                            avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                        }
                }
            }
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert innvilget`() {
            underkjentSøknadsbehandlingUføre(
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
            ).also { (_, underkjent) ->
                underkjent.shouldBeType<UnderkjentSøknadsbehandling.Avslag.MedBeregning>().also {
                    underkjent.oppdaterFradragsgrunnlagForSaksbehandler(
                        saksbehandler = saksbehandler,
                        emptyList(),
                        clock = fixedClock,
                    ).getOrFail()
                        .also { innvilget ->
                            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                        }
                }
            }
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert avslag`() {
            underkjentSøknadsbehandlingUføre(
                customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
            ).also { (_, underkjent) ->
                underkjent.shouldBeType<UnderkjentSøknadsbehandling.Avslag.MedBeregning>().also {
                    underkjent.leggTilInstitusjonsoppholdVilkår(
                        saksbehandler = saksbehandler,
                        institusjonsoppholdvilkårAvslag(),
                        clock = fixedClock,
                    ).getOrFail().also { avslag ->
                        avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
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
                it.leggTilFastOppholdINorgeVilkår(
                    saksbehandler = saksbehandler,
                    fastOppholdVilkårInnvilget(),
                    clock = fixedClock,
                )
                    .shouldBeType<Either.Left<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår.UgyldigTilstand>>()
                it.leggTilFormuegrunnlag(
                    request = LeggTilFormuevilkårRequest(
                        behandlingId = it.id,
                        formuegrunnlag = nonEmptyListOf(
                            LeggTilFormuevilkårRequest.Grunnlag.Søknadsbehandling(
                                periode = it.periode,
                                epsFormue = null,
                                søkersFormue = Formuegrunnlag.Verdier.empty(),
                                begrunnelse = null,
                                måInnhenteMerInformasjon = false,
                            ),
                        ),
                        saksbehandler = saksbehandler,
                        tidspunkt = fixedTidspunkt,
                    ),
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                ).shouldBeType<Either.Left<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.UgyldigTilstand>>()
            }
        }
    }

    @Nested
    inner class Beregnet {
        @Test
        fun `kan beregne for vilkårsvurdert innvilget`() {
            val vilkårsvurdertInnvilget = vilkårsvurdertInnvilget
            vilkårsvurdertInnvilget.beregn(
                begrunnelse = "123",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
                nySaksbehandler = saksbehandler,
                uteståendeAvkortingPåSak = null,
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
                uteståendeAvkortingPåSak = null,
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
                uteståendeAvkortingPåSak = null,
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
                uteståendeAvkortingPåSak = null,
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
                uteståendeAvkortingPåSak = null,
            ).getOrFail().let {
                it shouldBe beOfType<BeregnetSøknadsbehandling.Avslag>()
                it.saksbehandler shouldBe saksbehandler
                it.fritekstTilBrev shouldBe fritekstTilBrev
                it.attesteringer shouldBe Attesteringshistorikk.create(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering()))
                it.søknadsbehandlingsHistorikk shouldBe underkjentAvslagBeregning.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                    nonEmptyListOf(
                        nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                    ),
                )
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
                uteståendeAvkortingPåSak = null,
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
    }

    @Nested
    inner class TilAttestering {
        @Test
        fun `vilkårsvurder avslag til attestering`() {
            val vilkårsvurdertAvslag = vilkårsvurdertAvslag
            statusovergang(
                vilkårsvurdertAvslag,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev, fixedClock),
            ).getOrFail().let {
                it shouldBe beOfType<SøknadsbehandlingTilAttestering.Avslag.UtenBeregning>()
                it.saksbehandler shouldBe saksbehandler
                it.fritekstTilBrev shouldBe fritekstTilBrev
                it.søknadsbehandlingsHistorikk shouldBe vilkårsvurdertAvslag.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                    nonEmptyListOf(
                        nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                    ),
                )
            }
        }

        @Test
        fun `vilkårsvurder beregning til attestering`() {
            val beregnetAvslag = beregnetAvslag
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev, fixedClock),
            ).getOrFail().let {
                it shouldBe beOfType<SøknadsbehandlingTilAttestering.Avslag.MedBeregning>()
                it.saksbehandler shouldBe saksbehandler
                it.fritekstTilBrev shouldBe fritekstTilBrev
                it.søknadsbehandlingsHistorikk shouldBe beregnetAvslag.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                    nonEmptyListOf(
                        nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                    ),
                )
            }
        }

        @Test
        fun `simulert til attestering`() {
            val simulert = simulert
            statusovergang(
                simulert,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev, fixedClock),
            ).getOrFail().let {
                it shouldBe beOfType<SøknadsbehandlingTilAttestering.Innvilget>()
                it.saksbehandler shouldBe saksbehandler
                it.fritekstTilBrev shouldBe fritekstTilBrev
                it.søknadsbehandlingsHistorikk shouldBe simulert.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                    nonEmptyListOf(
                        nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                    ),
                )
            }
        }

        @Test
        fun `underkjent avslag vilkår til attestering`() {
            val underkjentAvslagVilkår = underkjentAvslagVilkår
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev, fixedClock),
            ).getOrFail().let {
                it shouldBe beOfType<SøknadsbehandlingTilAttestering.Avslag.UtenBeregning>()
                it.saksbehandler shouldBe saksbehandler
                it.fritekstTilBrev shouldBe fritekstTilBrev
                it.søknadsbehandlingsHistorikk shouldBe underkjentAvslagVilkår.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                    nonEmptyListOf(
                        nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                    ),
                )
            }
        }

        @Test
        fun `underkjent avslag beregning til attestering`() {
            val underkjentAvslagBeregning = underkjentAvslagBeregning
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev, fixedClock),
            ).getOrFail().let {
                it shouldBe beOfType<SøknadsbehandlingTilAttestering.Avslag.MedBeregning>()
                it.saksbehandler shouldBe saksbehandler
                it.fritekstTilBrev shouldBe fritekstTilBrev
                it.søknadsbehandlingsHistorikk shouldBe underkjentAvslagBeregning.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                    nonEmptyListOf(
                        nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                    ),
                )
            }
        }

        @Test
        fun `underkjent innvilging til attestering`() {
            val underkjentInnvilget = underkjentInnvilget
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev, fixedClock),
            ).getOrFail().let {
                it shouldBe beOfType<SøknadsbehandlingTilAttestering.Innvilget>()
                it.saksbehandler shouldBe saksbehandler
                it.fritekstTilBrev shouldBe fritekstTilBrev
                it.søknadsbehandlingsHistorikk shouldBe underkjentInnvilget.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                    nonEmptyListOf(
                        nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.SendtTilAttestering),
                    ),
                )
            }
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
                    Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev, fixedClock),
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
                    Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev, fixedClock),
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
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it::class.simpleName}") {
                    statusovergang(
                        it,
                        Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev, fixedClock),
                    )
                }
            }
        }
    }

    @Nested
    inner class TilUnderkjent {
        @Test
        fun `til attestering avslag vilkår til underkjent avslag vilkår`() {
            val attestering = attesteringUnderkjent(clock = fixedClock)
            val søknadsbehandling = tilAttesteringAvslagVilkår
            forsøkStatusovergang(
                søknadsbehandling,
                Statusovergang.TilUnderkjent(attestering),
            ).getOrFail().let {
                it shouldBe beOfType<UnderkjentSøknadsbehandling.Avslag.UtenBeregning>()
                it.saksbehandler shouldBe saksbehandler
                it.beregning shouldBe null
                it.fritekstTilBrev shouldBe fritekstTilBrev
                it.attesteringer shouldBe Attesteringshistorikk.create(listOf(attestering))
                it.søknadsbehandlingsHistorikk shouldBe søknadsbehandling.søknadsbehandlingsHistorikk
            }
        }

        @Test
        fun `til attestering avslag beregning til underkjent avslag beregning`() {
            val attestering = attesteringUnderkjent(clock = fixedClock)
            val tilAttesteringAvslagBeregning = tilAttesteringAvslagBeregning
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning,
                Statusovergang.TilUnderkjent(attestering),
            ).getOrFail().let {
                it shouldBe beOfType<UnderkjentSøknadsbehandling.Avslag.MedBeregning>()
                it.saksbehandler shouldBe saksbehandler
                it.beregning shouldNotBe null
                it.fritekstTilBrev shouldBe fritekstTilBrev
                it.attesteringer shouldBe Attesteringshistorikk.create(listOf(attestering))
                it.søknadsbehandlingsHistorikk shouldBe tilAttesteringAvslagBeregning.søknadsbehandlingsHistorikk
            }
        }

        @Test
        fun `til attestering innvilget til underkjent innvilging`() {
            val tilAttesteringInnvilget = tilAttesteringInnvilget
            val attestering = attesteringUnderkjent(clock = fixedClock)
            forsøkStatusovergang(
                tilAttesteringInnvilget,
                Statusovergang.TilUnderkjent(attestering),
            ).getOrFail().let {
                it shouldBe beOfType<UnderkjentSøknadsbehandling.Innvilget>()
                it.saksbehandler shouldBe saksbehandler
                it.beregning shouldBe tilAttesteringInnvilget.beregning
                it.fritekstTilBrev shouldBe fritekstTilBrev
                it.attesteringer shouldBe Attesteringshistorikk.create(listOf(attestering))
                it.søknadsbehandlingsHistorikk shouldBe tilAttesteringInnvilget.søknadsbehandlingsHistorikk
            }
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
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it::class.simpleName}") {
                    forsøkStatusovergang(
                        it,
                        Statusovergang.TilUnderkjent(attesteringUnderkjent(clock = fixedClock)),
                    )
                }
            }
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
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it::class.simpleName}") {
                forsøkStatusovergang(
                    it,
                    Statusovergang.TilUnderkjent(attesteringUnderkjent(clock = fixedClock)),
                )
            }
        }
    }

    @Nested
    // TODO jah: Denne bør egentlig flyttes til [OppdaterStønadsperiodeTest], men det vil kreve en del omskriving på setup-koden. Bør og gå via sak.
    inner class OppdaterStønadsperiode {
        private val aldersvurdering = Aldersvurdering.Vurdert(
            maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.avgjørBasertPåFødselsdatoEllerFødselsår(
                stønadsperiode,
                person().fødsel,
            ),
            saksbehandlersAvgjørelse = null,
            aldersinformasjon = Aldersinformasjon.createAldersinformasjon(
                person(),
                fixedClock,
            ),
        )

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
                it.oppdaterStønadsperiodeForSaksbehandler(
                    aldersvurdering = aldersvurdering,
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                    clock = fixedClock,
                    saksbehandler = saksbehandler,
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
                it.oppdaterStønadsperiodeForSaksbehandler(
                    aldersvurdering = aldersvurdering,
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                    clock = fixedClock,
                    saksbehandler = saksbehandler,
                ).isLeft() shouldBe true
            }
        }
    }
}
