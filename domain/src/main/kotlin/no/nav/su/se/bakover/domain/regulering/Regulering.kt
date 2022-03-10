package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

fun List<Regulering>.harÅpenRegulering() = this.any { it is Regulering.OpprettetRegulering }
fun List<Regulering>.hentÅpenRegulering() = this.filterIsInstance<Regulering.OpprettetRegulering>().single()

sealed interface Regulering : Behandling {
    val beregning: Beregning?
    val simulering: Simulering?
    val saksbehandler: NavIdentBruker.Saksbehandler
    val reguleringType: ReguleringType
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger

    sealed class KunneIkkeBeregne {
        object BeregningFeilet : KunneIkkeBeregne()
        data class IkkeLovÅBeregneIDenneStatusen(val status: KClass<out Regulering>) :
            KunneIkkeBeregne()
    }

    sealed class KunneIkkeSimulere {
        object FantIngenBeregning : KunneIkkeSimulere()
        object SimuleringFeilet : KunneIkkeSimulere()
    }

    fun beregn(clock: Clock, begrunnelse: String?): Either<KunneIkkeBeregne, OpprettetRegulering> =
        KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen(this::class).left()

    // TODO ai: Sørg for att vilkårsvurderingene er innvilget for HELE perioden.
    data class OpprettetRegulering(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val periode: Periode,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering,
        override val beregning: Beregning?,
        override val simulering: Simulering?,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val reguleringType: ReguleringType,
    ) : Regulering {
        override val grunnlagsdata: Grunnlagsdata
            get() = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering
            get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger

        fun leggTilFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): OpprettetRegulering =
            // er det ok å returnere this, eller skal man lage ny OpprettetRegulering som man returnerer her?
            // samme for beregn og simulering...
            this.copy(
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        bosituasjon = grunnlagsdata.bosituasjon,
                        fradragsgrunnlag = fradragsgrunnlag,
                    ).getOrHandle { throw IllegalStateException("") },
                    vilkårsvurderinger = vilkårsvurderinger

                )
            )

        override fun beregn(clock: Clock, begrunnelse: String?): Either<KunneIkkeBeregne, OpprettetRegulering> {
            val reguleringMedNyForventetInntekt = copy(
                grunnlagsdataOgVilkårsvurderinger = this.grunnlagsdataOgVilkårsvurderinger.copy(
                    vilkårsvurderinger = this.grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.copy(
                        uføre = (vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).regulerForventetInntekt().getOrHandle {
                            throw RuntimeException("")
                        }
                    )
                )
            )

            return reguleringMedNyForventetInntekt.gjørBeregning(
                begrunnelse = begrunnelse,
                clock = clock,
            ).map { reguleringMedNyForventetInntekt.copy(beregning = it) }
        }

        fun simuler(callback: (request: SimulerUtbetalingRequest.NyUtbetalingRequest) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>): Either<KunneIkkeSimulere, OpprettetRegulering> {
            if (beregning == null) {
                return KunneIkkeSimulere.FantIngenBeregning.left()
            }

            return callback(
                SimulerUtbetalingRequest.NyUtbetaling(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    beregning = beregning,
                    uføregrunnlag = vilkårsvurderinger.uføre.grunnlag,
                ),
            )
                .mapLeft { KunneIkkeSimulere.SimuleringFeilet }
                .map { this.copy(simulering = it.simulering) }
        }

        fun tilIverksatt(): IverksattRegulering = IverksattRegulering(opprettetRegulering = this)

        private fun gjørBeregning(
            begrunnelse: String?,
            clock: Clock,
        ): Either<KunneIkkeBeregne.BeregningFeilet, Beregning> {
            return Either.catch {
                BeregningStrategyFactory(clock).beregn(
                    grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                    ),
                    beregningsPeriode = periode,
                    begrunnelse = begrunnelse,
                )
            }.mapLeft { KunneIkkeBeregne.BeregningFeilet }
        }
    }

    data class IverksattRegulering(
        val opprettetRegulering: OpprettetRegulering,
        // TODO Her vil vi egentlig si at simulering og beregning ikke kan være nullable
    ) : Regulering by opprettetRegulering {
        // fun lagre status iverksatt og vedtak
    }
}
