package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.mai
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
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

enum class Reguleringsjobb(val jobbnavn: String, val dato: LocalDate) {
    G_REGULERING_2021("G_REGULERING_2021", 1.mai(2021)),
    G_REGULERING_2022("G_REGULERING_2022", 1.mai(2022)),
    SATS_REGULERING_2022("SATS_REGULERING_2022", 1.mai(2022))
}

sealed interface Regulering : Behandling {
    val beregning: Beregning?
    val simulering: Simulering?
    val saksbehandler: NavIdentBruker.Saksbehandler
    val reguleringType: ReguleringType
    val jobbnavn: Reguleringsjobb

    sealed class KunneIkkeBeregne {
        object BeregningFeilet : KunneIkkeBeregne()
        data class IkkeLovÅBeregneIDenneStatusen(val status: KClass<out Regulering>) :
            KunneIkkeBeregne()
    }

    fun beregn(clock: Clock, begrunnelse: String?): Either<KunneIkkeBeregne, OpprettetRegulering> =
        KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen(this::class).left()

    data class OpprettetRegulering(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val periode: Periode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val beregning: Beregning?,
        override val simulering: Simulering?,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val reguleringType: ReguleringType,
        override val jobbnavn: Reguleringsjobb,
    ) : Regulering {

        fun leggTilFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): OpprettetRegulering =
            // er det ok å returnere this, eller skal man lage ny OpprettetRegulering som man returnerer her?
            // samme for beregn og simulering...
            this.copy(
                grunnlagsdata = Grunnlagsdata.tryCreate(
                    bosituasjon = grunnlagsdata.bosituasjon,
                    fradragsgrunnlag = fradragsgrunnlag,
                ).getOrHandle { throw IllegalStateException("") },
            )

        override fun beregn(clock: Clock, begrunnelse: String?): Either<KunneIkkeBeregne, OpprettetRegulering> =
            gjørBeregning(
                regulering = this,
                begrunnelse = begrunnelse,
                clock = clock,
            ).map { this.copy(beregning = it) }

        fun leggTilSimulering(simulering: Simulering): OpprettetRegulering =
            this.copy(
                simulering = simulering,
            )

        fun tilIverksatt(): IverksattRegulering = IverksattRegulering(
            opprettetRegulering = this,
        )

        // fun oppdaterForventetInntekt(uførhet: Vilkår.Uførhet): OpprettetRegulering =
        //     this.copy(
        //         vilkårsvurderinger = vilkårsvurderinger.copy(
        //             uføre = uførhet
        //         )
        //     )

        private fun gjørBeregning(
            regulering: OpprettetRegulering,
            begrunnelse: String?,
            clock: Clock,
        ): Either<KunneIkkeBeregne.BeregningFeilet, Beregning> = Either.catch {
            BeregningStrategyFactory(clock).beregn(
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger(
                    grunnlagsdata = regulering.grunnlagsdata,
                    vilkårsvurderinger = regulering.vilkårsvurderinger,
                ),
                beregningsPeriode = regulering.periode,
                begrunnelse = begrunnelse,
            )
        }.mapLeft { KunneIkkeBeregne.BeregningFeilet }
    }

    data class IverksattRegulering(
        val opprettetRegulering: OpprettetRegulering,
        // TODO Her vil vi egentlig si at simulering og beregning ikke kan være nullable
    ) : Regulering by opprettetRegulering {
        // fun lagre status iverksatt og vedtak
    }
}
