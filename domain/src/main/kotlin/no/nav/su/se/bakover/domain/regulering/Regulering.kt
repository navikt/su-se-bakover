package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.SjekkOmGrunnlagErKonsistent
import no.nav.su.se.bakover.domain.grunnlag.erGyldigTilstand
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

fun Regulering.inneholderAvslag(): Boolean =
    this.grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.vurdering is Vilkårsvurderingsresultat.Avslag

interface Reguleringsfelter : Behandling {
    val beregning: Beregning?
    val simulering: Simulering?
    val saksbehandler: NavIdentBruker.Saksbehandler
    val reguleringstype: Reguleringstype
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger
}

sealed interface Regulering : Reguleringsfelter {
    fun erÅpen(): Boolean

    /**
     * true dersom dette er en iverksatt regulering, false ellers.
     */
    val erFerdigstilt: Boolean

    companion object {
        fun opprettRegulering(
            id: UUID = UUID.randomUUID(),
            sakId: UUID,
            saksnummer: Saksnummer,
            fnr: Fnr,
            gjeldendeVedtaksdata: GjeldendeVedtaksdata,
            clock: Clock,
            opprettet: Tidspunkt = Tidspunkt.now(clock),
            sakstype: Sakstype,
        ): Either<LagerIkkeReguleringDaDenneUansettMåRevurderes, OpprettetRegulering> {
            val reguleringstype = SjekkOmGrunnlagErKonsistent(gjeldendeVedtaksdata).resultat.fold(
                { konsistensproblemer ->
                    val message =
                        "Kunne ikke opprette regulering for saksnummer $saksnummer." +
                            " Grunnlag er ikke konsistente. Vi kan derfor ikke beregne denne. Vi klarer derfor ikke å bestemme om denne allerede er regulert. Problemer: [$konsistensproblemer]"
                    if (konsistensproblemer.erGyldigTilstand()) {
                        log.info(message)
                    } else {
                        log.error(message)
                    }
                    return LagerIkkeReguleringDaDenneUansettMåRevurderes.left()
                },
                {
                    gjeldendeVedtaksdata.utledReguleringstype()
                },
            )

            return OpprettetRegulering(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
                fnr = fnr,
                grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                beregning = null,
                simulering = null,
                reguleringstype = reguleringstype,
                sakstype = sakstype,
            ).right()
        }
    }

    object LagerIkkeReguleringDaDenneUansettMåRevurderes

    sealed interface KunneIkkeBeregne {
        data class BeregningFeilet(val feil: Throwable) : KunneIkkeBeregne
        data class IkkeLovÅBeregneIDenneStatusen(val status: KClass<out Regulering>) :
            KunneIkkeBeregne
    }

    sealed interface KunneIkkeSimulere {
        object FantIngenBeregning : KunneIkkeSimulere
        object SimuleringFeilet : KunneIkkeSimulere
    }

    data class OpprettetRegulering(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering,
        override val periode: Periode = grunnlagsdataOgVilkårsvurderinger.periode()!!,
        override val beregning: Beregning?,
        override val simulering: Simulering?,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val reguleringstype: Reguleringstype,
        override val sakstype: Sakstype,
    ) : Regulering {
        override fun erÅpen() = true

        override val erFerdigstilt = false

        override val grunnlagsdata: Grunnlagsdata
            get() = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering
            get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger

        init {
            if (reguleringstype == Reguleringstype.AUTOMATISK) {
                assert(vilkårsvurderinger.vurdering is Vilkårsvurderingsresultat.Innvilget)
            }
            assert(grunnlagsdataOgVilkårsvurderinger.erVurdert())
            assert(periode == grunnlagsdataOgVilkårsvurderinger.periode())
            beregning?.let { assert(periode == beregning.periode) }
        }

        fun leggTilFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): OpprettetRegulering =
            this.copy(
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        bosituasjon = grunnlagsdata.bosituasjon,
                        fradragsgrunnlag = fradragsgrunnlag,
                    ).getOrHandle { throw IllegalStateException("") },
                    vilkårsvurderinger = vilkårsvurderinger,
                ),
            )

        fun leggTilUføre(uføregrunnlag: List<Grunnlag.Uføregrunnlag>, clock: Clock): OpprettetRegulering =
            this.copy(
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger.leggTil(
                        UføreVilkår.Vurdert.tryCreate(
                            uføregrunnlag.map {
                                VurderingsperiodeUføre.tryCreate(
                                    opprettet = Tidspunkt.now(clock),
                                    vurdering = Vurdering.Innvilget,
                                    grunnlag = it,
                                    vurderingsperiode = it.periode,
                                ).getOrHandle { throw RuntimeException("$it") }
                            }.toNonEmptyList(),
                        ).getOrHandle { throw RuntimeException("$it") },
                    ),
                ),
            )

        fun leggTilSaksbehandler(saksbehandler: NavIdentBruker.Saksbehandler): OpprettetRegulering =
            this.copy(
                saksbehandler = saksbehandler,
            )

        fun beregn(
            satsFactory: SatsFactory,
            begrunnelse: String? = null,
            clock: Clock,
        ): Either<KunneIkkeBeregne, OpprettetRegulering> {
            return this.gjørBeregning(
                satsFactory = satsFactory,
                begrunnelse = begrunnelse,
                clock = clock,
            ).map { this.copy(beregning = it) }
        }

        fun simuler(
            callback: (request: SimulerUtbetalingRequest.NyUtbetalingRequest) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
        ): Either<KunneIkkeSimulere, OpprettetRegulering> {
            return simulerUtbetalingRequest()
                .mapLeft {
                    KunneIkkeSimulere.FantIngenBeregning
                }
                .flatMap { simulertUtbetaling ->
                    callback(simulertUtbetaling)
                        .mapLeft { KunneIkkeSimulere.SimuleringFeilet }
                        .map { copy(simulering = it.simulering) }
                }
        }

        fun simulerUtbetalingRequest(): Either<KunneIkkeSimulere.FantIngenBeregning, SimulerUtbetalingRequest.NyUtbetaling> {
            if (beregning == null) {
                return KunneIkkeSimulere.FantIngenBeregning.left()
            }

            return when (sakstype) {
                Sakstype.ALDER -> {
                    SimulerUtbetalingRequest.NyUtbetaling.Alder(
                        sakId = sakId,
                        saksbehandler = saksbehandler,
                        beregning = beregning,
                        // Spesielt for regulering, ved etterbetaling, ønsker vi å utbetale disse sammen med neste kjøring, da disse beløpene bruker å være relativt små.
                        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling,
                    )
                }

                Sakstype.UFØRE -> {
                    SimulerUtbetalingRequest.NyUtbetaling.Uføre(
                        sakId = sakId,
                        saksbehandler = saksbehandler,
                        beregning = beregning,
                        uføregrunnlag = vilkårsvurderinger.uføreVilkår()
                            .getOrHandle { throw IllegalStateException("Regulering for uføre mangler uføregrunnlag") }.grunnlag,
                        // Spesielt for regulering, ved etterbetaling, ønsker vi å utbetale disse sammen med neste kjøring, da disse beløpene bruker å være relativt små.
                        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling,
                    )
                }
            }.right()
        }

        fun avslutt(clock: Clock): AvsluttetRegulering {
            return AvsluttetRegulering(
                opprettetRegulering = this,
                avsluttetTidspunkt = Tidspunkt.now(clock),
            )
        }

        fun tilIverksatt(): IverksattRegulering =
            IverksattRegulering(opprettetRegulering = this, beregning!!, simulering!!)

        private fun gjørBeregning(
            satsFactory: SatsFactory,
            begrunnelse: String?,
            clock: Clock,
        ): Either<KunneIkkeBeregne.BeregningFeilet, Beregning> {
            return Either.catch {
                BeregningStrategyFactory(
                    clock = clock,
                    satsFactory = satsFactory,
                ).beregn(this, begrunnelse)
            }.mapLeft {
                KunneIkkeBeregne.BeregningFeilet(feil = it)
            }
        }
    }

    data class IverksattRegulering(
        /**
         * Denne er gjort public pga å gjøre den testbar fra databasen siden vi må kunne gjøre den persistert
         */
        val opprettetRegulering: OpprettetRegulering,
        override val beregning: Beregning,
        override val simulering: Simulering,
    ) : Regulering, Reguleringsfelter by opprettetRegulering {
        override fun erÅpen(): Boolean = false

        override val erFerdigstilt = true

        fun verifiserOgSimulerUtbetaling(
            callback: (request: UtbetalRequest.NyUtbetaling) -> Either<UtbetalingFeilet, Utbetaling.SimulertUtbetaling>,
        ): Either<UtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
            return opprettetRegulering.simulerUtbetalingRequest()
                .getOrHandle { throw IllegalStateException(it.toString()) }
                .let {
                    callback(
                        UtbetalRequest.NyUtbetaling(
                            request = it,
                            simulering = simulering,
                        ),
                    )
                }
        }
    }

    data class AvsluttetRegulering(
        val opprettetRegulering: OpprettetRegulering,
        val avsluttetTidspunkt: Tidspunkt,
    ) : Regulering by opprettetRegulering {
        override fun erÅpen(): Boolean = false
        override val erFerdigstilt = true
    }
}
