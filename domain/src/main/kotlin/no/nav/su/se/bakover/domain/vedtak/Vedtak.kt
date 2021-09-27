package no.nav.su.se.bakover.domain.vedtak

import arrow.core.Nel
import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn.Companion.toAvslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.fullstendigOrThrow
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.domain.visitor.SkalSendeBrevVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock
import java.util.UUID

interface VedtakFelles {
    val id: UUID
    val opprettet: Tidspunkt
    val behandling: Behandling
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant
    val periode: Periode
}

sealed interface VedtakSomKanRevurderes : VedtakFelles

sealed class Vedtak : VedtakFelles, Visitable<VedtakVisitor> {

    fun skalSendeBrev(): Boolean {
        return SkalSendeBrevVisitor().let {
            this.accept(it)
            it.sendBrev
        }
    }

    companion object {
        fun fromSøknadsbehandling(
            søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
            utbetalingId: UUID30,
            clock: Clock,
        ) =
            EndringIYtelse.InnvilgetSøknadsbehandling(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = søknadsbehandling.periode,
                behandling = søknadsbehandling,
                beregning = søknadsbehandling.beregning,
                simulering = søknadsbehandling.simulering,
                saksbehandler = søknadsbehandling.saksbehandler,
                attestant = søknadsbehandling.attesteringer.hentSisteAttestering().attestant,
                utbetalingId = utbetalingId,
            )

        fun from(revurdering: IverksattRevurdering.IngenEndring, clock: Clock) = IngenEndringIYtelse(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            behandling = revurdering,
            periode = revurdering.periode,
            beregning = revurdering.beregning,
            saksbehandler = revurdering.saksbehandler,
            attestant = revurdering.attestering.attestant,
        )

        fun from(revurdering: IverksattRevurdering.Innvilget, utbetalingId: UUID30, clock: Clock) =
            EndringIYtelse.InnvilgetRevurdering(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                behandling = revurdering,
                periode = revurdering.periode,
                beregning = revurdering.beregning,
                simulering = revurdering.simulering,
                saksbehandler = revurdering.saksbehandler,
                attestant = revurdering.attestering.attestant,
                utbetalingId = utbetalingId,
            )

        fun from(revurdering: IverksattRevurdering.Opphørt, utbetalingId: UUID30, clock: Clock) =
            EndringIYtelse.OpphørtRevurdering(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                behandling = revurdering,
                periode = revurdering.periode,
                beregning = revurdering.beregning,
                simulering = revurdering.simulering,
                saksbehandler = revurdering.saksbehandler,
                attestant = revurdering.attestering.attestant,
                utbetalingId = utbetalingId,
            )

        fun from(
            revurdering: StansAvYtelseRevurdering.IverksattStansAvYtelse,
            utbetalingId: UUID30,
            clock: Clock,
        ): EndringIYtelse.StansAvYtelse {
            return EndringIYtelse.StansAvYtelse(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                behandling = revurdering,
                periode = revurdering.periode,
                simulering = revurdering.simulering,
                saksbehandler = revurdering.saksbehandler,
                attestant = revurdering.attesteringer.hentSisteAttestering().attestant,
                utbetalingId = utbetalingId,
            )
        }

        fun from(
            revurdering: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse,
            utbetalingId: UUID30,
            clock: Clock,
        ): EndringIYtelse.GjenopptakAvYtelse {
            return EndringIYtelse.GjenopptakAvYtelse(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                behandling = revurdering,
                periode = revurdering.periode,
                simulering = revurdering.simulering,
                saksbehandler = revurdering.saksbehandler,
                attestant = revurdering.attesteringer.hentSisteAttestering().attestant,
                utbetalingId = utbetalingId,
            )
        }
    }

    sealed class EndringIYtelse : Vedtak(), VedtakSomKanRevurderes {
        abstract override val id: UUID
        abstract override val opprettet: Tidspunkt
        abstract override val behandling: Behandling
        abstract override val saksbehandler: NavIdentBruker.Saksbehandler
        abstract override val attestant: NavIdentBruker.Attestant
        abstract override val periode: Periode
        abstract val simulering: Simulering
        abstract val utbetalingId: UUID30

        data class InnvilgetSøknadsbehandling(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val behandling: Søknadsbehandling.Iverksatt.Innvilget,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val periode: Periode,
            val beregning: Beregning,
            override val simulering: Simulering,
            override val utbetalingId: UUID30,
        ) : EndringIYtelse() {

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }
        }

        data class InnvilgetRevurdering(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val behandling: IverksattRevurdering.Innvilget,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val periode: Periode,
            val beregning: Beregning,
            override val simulering: Simulering,
            override val utbetalingId: UUID30,
        ) : EndringIYtelse() {

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }
        }

        data class OpphørtRevurdering(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val behandling: IverksattRevurdering.Opphørt,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val periode: Periode,
            val beregning: Beregning,
            override val simulering: Simulering,
            override val utbetalingId: UUID30,
        ) : EndringIYtelse() {

            fun utledOpphørsgrunner() = behandling.utledOpphørsgrunner()

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }
        }

        data class StansAvYtelse(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val behandling: StansAvYtelseRevurdering.IverksattStansAvYtelse,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val periode: Periode,
            override val simulering: Simulering,
            override val utbetalingId: UUID30,
        ) : EndringIYtelse() {

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }
        }

        data class GjenopptakAvYtelse(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val behandling: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val periode: Periode,
            override val simulering: Simulering,
            override val utbetalingId: UUID30,
        ) : EndringIYtelse() {

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }
        }
    }

    data class IngenEndringIYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val behandling: IverksattRevurdering.IngenEndring,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant,
        override val periode: Periode,
        val beregning: Beregning,
    ) : Vedtak(), VedtakSomKanRevurderes {

        override fun accept(visitor: VedtakVisitor) {
            visitor.visit(this)
        }
    }

    sealed class Avslag : Vedtak(), ErAvslag {

        companion object {
            fun fromSøknadsbehandlingMedBeregning(
                avslag: Søknadsbehandling.Iverksatt.Avslag.MedBeregning,
                clock: Clock,
            ) =
                AvslagBeregning(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    behandling = avslag,
                    beregning = avslag.beregning,
                    saksbehandler = avslag.saksbehandler,
                    attestant = avslag.attesteringer.hentSisteAttestering().attestant,
                    periode = avslag.periode,
                )

            fun fromSøknadsbehandlingUtenBeregning(
                avslag: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
                clock: Clock,
            ) =
                AvslagVilkår(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    behandling = avslag,
                    saksbehandler = avslag.saksbehandler,
                    attestant = avslag.attesteringer.hentSisteAttestering().attestant,
                    periode = avslag.periode,
                )
        }

        data class AvslagVilkår(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val behandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val periode: Periode,
        ) : Avslag() {
            // TODO jah: I en overgangsfase vil vilkårsvurderingene finnes både i Behandlingsinformasjon og Vilkårsvurderinger, ideelt sett hadde Vilkårsvurderinger eid avslagsgrunnene.
            override val avslagsgrunner: List<Avslagsgrunn> = behandling.behandlingsinformasjon.utledAvslagsgrunner()

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }
        }

        data class AvslagBeregning(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val behandling: Søknadsbehandling.Iverksatt.Avslag.MedBeregning,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val periode: Periode,
            val beregning: Beregning,
        ) : Avslag() {
            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            // TODO jm: disse bør sannsynligvis peristeres.
            // TODO jah: I en overgangsfase vil vilkårsvurderingene finnes både i Behandlingsinformasjon og Vilkårsvurderinger, ideelt sett hadde Vilkårsvurderinger eid avslagsgrunnene.
            override val avslagsgrunner: List<Avslagsgrunn> =
                behandling.behandlingsinformasjon.utledAvslagsgrunner() + avslagsgrunnForBeregning

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }
        }
    }

    /**
     * Representerer et vedtak plassert på en tidslinje utledet fra vedtakenes temporale gyldighet.
     * I denne sammenhen er et vedtak ansett som gyldig inntil det utløper eller overskrives (helt/delvis) av et nytt.
     * Ved plassering på tidslinja gjennom [KanPlasseresPåTidslinje], er objektet ansvarlig for at alle periodiserbare
     * opplysninger som ligger til grunn for vedtaket justeres i henhold til aktuell periode gitt av [CopyArgs.Tidslinje].
     */
    data class VedtakPåTidslinje(
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        val grunnlagsdata: Grunnlagsdata,
        val vilkårsvurderinger: Vilkårsvurderinger,
        /**
         * Referanse til det originale vedtaket dette tidslinje-elementet er basert på. Må ikke endres eller benyttes
         * til uthenting av grunnlagsdata.
         */
        val originaltVedtak: VedtakSomKanRevurderes,
    ) : KanPlasseresPåTidslinje<VedtakPåTidslinje> {
        override fun copy(args: CopyArgs.Tidslinje): VedtakPåTidslinje =
            when (args) {
                CopyArgs.Tidslinje.Full -> {
                    val uførevilkår = when (vilkårsvurderinger.uføre) {
                        Vilkår.Uførhet.IkkeVurdert -> Vilkår.Uførhet.IkkeVurdert
                        is Vilkår.Uførhet.Vurdert -> {
                            val vurderingsperioder: NonEmptyList<Vurderingsperiode.Uføre> = Nel.fromListUnsafe(
                                Tidslinje(
                                    periode = periode,
                                    objekter = vilkårsvurderinger.uføre.vurderingsperioder,
                                ).tidslinje,
                            )
                            vilkårsvurderinger.uføre.copy(
                                vurderingsperioder = vurderingsperioder,
                            )
                        }
                    }
                    val formue = when (vilkårsvurderinger.formue) {
                        is Vilkår.Formue.IkkeVurdert -> Vilkår.Formue.IkkeVurdert
                        is Vilkår.Formue.Vurdert -> {
                            val vurderingsperioder: NonEmptyList<Vurderingsperiode.Formue> = Nel.fromListUnsafe(
                                Tidslinje(
                                    periode = periode,
                                    objekter = vilkårsvurderinger.formue.vurderingsperioder,
                                ).tidslinje,
                            )
                            vilkårsvurderinger.formue.copy(
                                vurderingsperioder = vurderingsperioder,
                            )
                        }
                    }
                    copy(
                        periode = periode,
                        grunnlagsdata = Grunnlagsdata.create(
                            bosituasjon = grunnlagsdata.bosituasjon.mapNotNull {
                                (it.fullstendigOrThrow()).copy(
                                    CopyArgs.Snitt(periode),
                                )
                            },
                            fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag.mapNotNull {
                                it.copy(args = CopyArgs.Snitt(periode))
                            },
                        ),
                        vilkårsvurderinger = Vilkårsvurderinger(
                            uføre = uførevilkår,
                            formue = formue,
                        ),
                        originaltVedtak = originaltVedtak,
                    )
                }
                is CopyArgs.Tidslinje.NyPeriode -> {
                    val uførevilkår = when (this.vilkårsvurderinger.uføre) {
                        Vilkår.Uførhet.IkkeVurdert -> Vilkår.Uførhet.IkkeVurdert
                        is Vilkår.Uførhet.Vurdert -> this.vilkårsvurderinger.uføre.copy(
                            vurderingsperioder = Nel.fromListUnsafe(
                                Tidslinje(
                                    periode = args.periode,
                                    objekter = this.vilkårsvurderinger.uføre.vurderingsperioder,
                                ).tidslinje,
                            ),
                        )
                    }
                    val formue = when (this.vilkårsvurderinger.formue) {
                        Vilkår.Formue.IkkeVurdert -> Vilkår.Formue.IkkeVurdert
                        is Vilkår.Formue.Vurdert -> this.vilkårsvurderinger.formue.copy(
                            vurderingsperioder = Nel.fromListUnsafe(
                                Tidslinje(
                                    periode = args.periode,
                                    objekter = this.vilkårsvurderinger.formue.vurderingsperioder,
                                ).tidslinje,
                            ),
                        )
                    }
                    copy(
                        periode = args.periode,
                        grunnlagsdata = Grunnlagsdata.create(
                            bosituasjon = grunnlagsdata.bosituasjon.mapNotNull {
                                (it.fullstendigOrThrow()).copy(
                                    CopyArgs.Snitt(args.periode),
                                )
                            },
                            fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag.filterNot {
                                it.fradragstype == Fradragstype.ForventetInntekt
                            }.mapNotNull {
                                it.copy(args = CopyArgs.Snitt(args.periode))
                            },
                        ),
                        vilkårsvurderinger = Vilkårsvurderinger(
                            uføre = uførevilkår,
                            formue = formue,
                        ),
                        originaltVedtak = originaltVedtak,
                    )
                }
            }
    }
}

// TODO: ("Må sees i sammenheng med evt endringer knyttet til hvilke vedtakstyper som legges til grunn for revurdering")
fun List<VedtakSomKanRevurderes>.lagTidslinje(periode: Periode, clock: Clock): Tidslinje<Vedtak.VedtakPåTidslinje> =
    map {
        Vedtak.VedtakPåTidslinje(
            opprettet = it.opprettet,
            periode = it.periode,
            grunnlagsdata = it.behandling.grunnlagsdata,
            vilkårsvurderinger = it.behandling.vilkårsvurderinger.copy(
                formue = it.behandling.let { behandling ->
                    // TODO jah: For Søknadsbehandling, migrer behandlingsinformasjon.formue til vilkårsvurderinger.formue
                    if (behandling is Søknadsbehandling) behandling.behandlingsinformasjon.formue!!.tilVilkår(
                        stønadsperiode = behandling.stønadsperiode!!,
                        bosituasjon = behandling.grunnlagsdata.bosituasjon,
                        clock = clock,
                    ) else behandling.vilkårsvurderinger.formue
                },
            ),
            originaltVedtak = it,
        )
    }.let {
        Tidslinje(
            periode = periode,
            objekter = it,
        )
    }
