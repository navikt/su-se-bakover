package no.nav.su.se.bakover.domain.vedtak

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.fullstendigOrThrow
import no.nav.su.se.bakover.domain.grunnlag.lagTidslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.visitor.SkalSendeBrevVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock
import java.util.UUID

/**
 * Vedtak som er knyttet til:
 * - en stønadsperiode (søknadsbehandlinger)
 * - en periode som kan være deler av en stønadsperiode eller på tvers av stønadsperioder (revurdering)
 */
sealed interface Stønadsvedtak : Vedtak, Visitable<VedtakVisitor> {
    val periode: Periode
    val behandling: Behandling

    fun erOpphør() = this is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering
    fun erStans() = this is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse
    fun erGjenopptak() = this is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse

    fun skalSendeBrev(): Boolean {
        return SkalSendeBrevVisitor().let {
            this.accept(it)
            it.sendBrev
        }
    }

    /**
     * Dersom grunnlaget inneholder fradrag av typen [Fradragstype.AvkortingUtenlandsopphold] vet vi at vedtaket
     * aktivt avkorter ytelsen.
     */
    fun harPågåendeAvkorting(): Boolean {
        return behandling.grunnlagsdata.fradragsgrunnlag.any { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
    }

    /**
     * Vedtaket fører med seg eg behov for avkorting av ytelsen i fremtiden.
     * Avkortingen av aktuelle beløp er enda ikke påbegynt, men behovet er identifisert.
     */
    fun harIdentifisertBehovForFremtidigAvkorting(): Boolean
}

/**
 * Per tidspunkt støtter vi ikke å revurdere Søknadsbehandlinger som førte til Avslag.
 * Når vi kan revurderer Avslag, kan man fjerne wrapperen `sealed interface VedtakSomKanRevurderes`
 */
sealed interface VedtakSomKanRevurderes : Stønadsvedtak {
    override val id: UUID
    override val opprettet: Tidspunkt
    override val saksbehandler: NavIdentBruker.Saksbehandler
    override val attestant: NavIdentBruker.Attestant
    override val periode: Periode
    override val behandling: Behandling

    companion object {
        fun fromSøknadsbehandling(
            søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
            utbetalingId: UUID30,
            clock: Clock,
        ) = EndringIYtelse.InnvilgetSøknadsbehandling(
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

        fun from(
            regulering: Regulering.IverksattRegulering,
            utbetalingId: UUID30,
            clock: Clock,
        ): EndringIYtelse.InnvilgetRegulering {
            return EndringIYtelse.InnvilgetRegulering(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                behandling = regulering,
                periode = regulering.periode,
                beregning = regulering.beregning,
                simulering = regulering.simulering,
                saksbehandler = regulering.saksbehandler,
                attestant = NavIdentBruker.Attestant(regulering.saksbehandler.toString()),
                utbetalingId = utbetalingId,
            )
        }
    }

    sealed interface EndringIYtelse : VedtakSomKanRevurderes {
        abstract override val id: UUID
        abstract override val opprettet: Tidspunkt
        abstract override val behandling: Behandling
        abstract override val saksbehandler: NavIdentBruker.Saksbehandler
        abstract override val attestant: NavIdentBruker.Attestant
        abstract override val periode: Periode
        val simulering: Simulering
        val utbetalingId: UUID30

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
        ) : EndringIYtelse {
            override fun harIdentifisertBehovForFremtidigAvkorting() = false

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
        ) : EndringIYtelse {
            override fun harIdentifisertBehovForFremtidigAvkorting() =
                behandling.avkorting is AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }
        }

        data class InnvilgetRegulering(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val behandling: Regulering.IverksattRegulering,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val periode: Periode,
            val beregning: Beregning,
            override val simulering: Simulering,
            override val utbetalingId: UUID30,
        ) : EndringIYtelse {
            override fun harIdentifisertBehovForFremtidigAvkorting() = false

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
        ) : EndringIYtelse {
            fun utledOpphørsgrunner(clock: Clock) = behandling.utledOpphørsgrunner(clock)

            override fun harIdentifisertBehovForFremtidigAvkorting() =
                behandling.avkorting is AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel

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
        ) : EndringIYtelse {
            override fun harIdentifisertBehovForFremtidigAvkorting() = false

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
        ) : EndringIYtelse {
            override fun harIdentifisertBehovForFremtidigAvkorting() = false

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
    ) : VedtakSomKanRevurderes {
        override fun harIdentifisertBehovForFremtidigAvkorting() =
            behandling.avkorting is AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel

        override fun accept(visitor: VedtakVisitor) {
            visitor.visit(this)
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
                    copy(
                        periode = periode,
                        grunnlagsdata = Grunnlagsdata.create(
                            bosituasjon = grunnlagsdata.bosituasjon.map {
                                it.fullstendigOrThrow()
                            }.lagTidslinje(periode),
                            /**
                             * TODO("dette ser ut som en bug, bør vel kvitte oss med forventet inntekt her og")
                             * Se hva vi gjør for NyPeriode litt lenger ned i denne funksjonen.
                             * Dersom dette grunnlaget brukes til en ny revurdering ønsker vi vel at forventet inntekt
                             * utledes på nytt fra grunnlaget i uførevilkåret? Kan vi potensielt ende opp med at vi
                             * får dobbelt opp med fradrag for forventet inntekt?
                             */
                            fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag.mapNotNull {
                                it.copy(args = CopyArgs.Snitt(periode))
                            },
                        ),
                        vilkårsvurderinger = vilkårsvurderinger.lagTidslinje(periode),
                        originaltVedtak = originaltVedtak,
                    )
                }
                is CopyArgs.Tidslinje.NyPeriode -> {
                    copy(
                        periode = args.periode,
                        grunnlagsdata = Grunnlagsdata.create(
                            bosituasjon = grunnlagsdata.bosituasjon.map {
                                it.fullstendigOrThrow()
                            }.lagTidslinje(args.periode),
                            fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag.filterNot {
                                it.fradragstype == Fradragstype.ForventetInntekt
                            }.mapNotNull {
                                it.copy(args = CopyArgs.Snitt(args.periode))
                            },
                        ),
                        vilkårsvurderinger = vilkårsvurderinger.lagTidslinje(args.periode),
                        originaltVedtak = originaltVedtak,
                    )
                }
                is CopyArgs.Tidslinje.Maskert -> {
                    copy(args.args).copy(opprettet = opprettet.plusUnits(1))
                }
            }

        fun erOpphør(): Boolean {
            return originaltVedtak.erOpphør()
        }

        fun erStans(): Boolean {
            return originaltVedtak.erStans()
        }

        fun erGjenopptak(): Boolean {
            return originaltVedtak.erGjenopptak()
        }

        fun uføreVilkår(): Either<Vilkårsvurderinger.VilkårEksistererIkke, Vilkår.Uførhet> {
            return vilkårsvurderinger.uføreVilkår()
        }

        fun lovligOppholdVilkår() = vilkårsvurderinger.lovligOppholdVilkår()

        fun formueVilkår(): Vilkår.Formue {
            return vilkårsvurderinger.formueVilkår()
        }

        fun utenlandsoppholdVilkår(): UtenlandsoppholdVilkår {
            return vilkårsvurderinger.utenlandsoppholdVilkår()
        }

        fun opplysningspliktVilkår(): OpplysningspliktVilkår {
            return vilkårsvurderinger.opplysningspliktVilkår()
        }

        fun pensjonsVilkår() = vilkårsvurderinger.pensjonsVilkår()

        fun familiegjenforeningvilkår() = vilkårsvurderinger.familiegjenforening()
    }
}

sealed interface Avslagsvedtak : Stønadsvedtak, Visitable<VedtakVisitor>, ErAvslag {
    override val periode: Periode
    override val behandling: Søknadsbehandling.Iverksatt.Avslag

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
                avslagsgrunner = avslag.avslagsgrunner,
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
                avslagsgrunner = avslag.avslagsgrunner,
            )

        fun fromAvslagManglendeDokumentasjon(
            avslag: AvslagManglendeDokumentasjon,
            clock: Clock,
        ): AvslagVilkår {
            return AvslagVilkår(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                behandling = avslag.søknadsbehandling,
                saksbehandler = avslag.søknadsbehandling.saksbehandler,
                attestant = avslag.søknadsbehandling.attesteringer.hentSisteAttestering().attestant,
                periode = avslag.søknadsbehandling.periode,
                avslagsgrunner = avslag.avslagsgrunner,
            )
        }
    }

    data class AvslagVilkår(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant,
        override val avslagsgrunner: List<Avslagsgrunn>,
        override val behandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
        override val periode: Periode,
    ) : Avslagsvedtak {
        override fun harIdentifisertBehovForFremtidigAvkorting() = false
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
        override val avslagsgrunner: List<Avslagsgrunn>,
    ) : Avslagsvedtak {
        override fun harIdentifisertBehovForFremtidigAvkorting() = false

        override fun accept(visitor: VedtakVisitor) {
            visitor.visit(this)
        }
    }
}

// TODO: ("Må sees i sammenheng med evt endringer knyttet til hvilke vedtakstyper som legges til grunn for revurdering")
fun List<VedtakSomKanRevurderes>.lagTidslinje(periode: Periode): Tidslinje<VedtakSomKanRevurderes.VedtakPåTidslinje> =
    map {
        VedtakSomKanRevurderes.VedtakPåTidslinje(
            opprettet = it.opprettet,
            periode = it.periode,
            grunnlagsdata = it.behandling.grunnlagsdata,
            vilkårsvurderinger = it.behandling.vilkårsvurderinger,
            originaltVedtak = it,
        )
    }.let {
        Tidslinje(
            periode = periode,
            objekter = it,
        )
    }
