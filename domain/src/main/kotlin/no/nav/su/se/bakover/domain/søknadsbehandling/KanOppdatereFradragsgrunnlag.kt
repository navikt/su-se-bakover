package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.perioder
import java.time.Clock

/**
 * Denne skiller seg fra [KanOppdaterePeriodeBosituasjonVilkår] ved at man kun kan oppdatere fradrag dersom de initielle vilkårene er innvilget.
 */
sealed interface KanOppdatereFradragsgrunnlag : Søknadsbehandling, KanOppdaterePeriodeGrunnlagVilkår {
    /**
     * Oppdaterer fradragsgrunnlag, legger til en ny hendelse og endrer tilstand til [VilkårsvurdertSøknadsbehandling.Innvilget]
     * For å fjerne alle fradragsgrunnlag, sendes en tom liste.
     *
     * Merk at vi sletter beregningen og simuleringer.
     */
    fun oppdaterFradragsgrunnlag(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag, VilkårsvurdertSøknadsbehandling.Innvilget> {
        validerFradragsgrunnlag(fradragsgrunnlag).onLeft { return it.left() }
        return (
            VilkårsvurdertSøknadsbehandling.opprett(
                forrigeTilstand = this,
                saksbehandler = saksbehandler,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFradragsgrunnlag(
                    fradragsgrunnlag,
                ),
                tidspunkt = Tidspunkt.now(clock),
                handling = SøknadsbehandlingsHandling.OppdatertFradragsgrunnlag,
            ) as VilkårsvurdertSøknadsbehandling.Innvilget
            ).right() // TODO cast - kan kanskje kalle oppdaterFradragsgrunnlag på VilkårsvurdertSøknadsbehandling.Innvilget?
    }

    private fun validerFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag, Unit> {
        if (fradragsgrunnlag.isNotEmpty()) {
            if (!periode.inneholder(fradragsgrunnlag.perioder())) {
                return KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden.left()
            }
        }
        return Unit.right()
    }
}
