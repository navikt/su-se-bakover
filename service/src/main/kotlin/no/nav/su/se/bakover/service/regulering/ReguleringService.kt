package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import java.time.LocalDate
import java.util.UUID

data class BeregnRequest(
    val behandlingId: UUID,
    val begrunnelse: String?,
)

sealed class KunneIkkeRegulereAutomatisk {
    object KunneIkkeBeregne : KunneIkkeRegulereAutomatisk()
    object KunneIkkeSimulere : KunneIkkeRegulereAutomatisk()
    object KunneIkkeUtbetale : KunneIkkeRegulereAutomatisk()
    object KanIkkeAutomatiskRegulereSomFørerTilFeilutbetaling : KunneIkkeRegulereAutomatisk()
}

sealed class KunneIkkeRegulereManuelt {
    object foo : KunneIkkeRegulereManuelt()
    object FantIkkeRegulering : KunneIkkeRegulereManuelt()
    object SimuleringFeilet : KunneIkkeRegulereManuelt()
    object BeregningFeilet : KunneIkkeRegulereManuelt()
    object AlleredeFerdigstilt : KunneIkkeRegulereManuelt()
    object FantIkkeSak : KunneIkkeRegulereManuelt()
    object StansetYtelseMåStartesFørDenKanReguleres : KunneIkkeRegulereManuelt()
}

sealed class BeregnOgSimulerFeilet {
    object FantIkkeRegulering : BeregnOgSimulerFeilet()
    object KunneIkkeBeregne : BeregnOgSimulerFeilet()
    object KunneIkkeSimulere : BeregnOgSimulerFeilet()
}

sealed class KunneIkkeIverksetteRegulering {
    object ReguleringErAlleredeIverksatt : KunneIkkeIverksetteRegulering()
    object FantIkkeRegulering : KunneIkkeIverksetteRegulering()
}

sealed class KunneIkkeLeggeTilFradrag {
    object ReguleringErAlleredeIverksatt : KunneIkkeLeggeTilFradrag()
    object FantIkkeRegulering : KunneIkkeLeggeTilFradrag()
}

sealed class KunneIkkeOppretteRegulering {
    object FantIkkeSak : KunneIkkeOppretteRegulering()
    object FørerIkkeTilEnEndring : KunneIkkeOppretteRegulering()
    data class KunneIkkeHenteEllerOppretteRegulering(val feil: Sak.KunneIkkeOppretteEllerOppdatereRegulering) : KunneIkkeOppretteRegulering()
    data class KunneIkkeRegulereAutomatisk(val feil: no.nav.su.se.bakover.service.regulering.KunneIkkeRegulereAutomatisk) : KunneIkkeOppretteRegulering()
}

interface ReguleringService {
    fun startRegulering(startDato: LocalDate): List<Either<KunneIkkeOppretteRegulering, Regulering>>
    fun leggTilFradrag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradrag, Regulering>
    fun iverksett(reguleringId: UUID): Either<KunneIkkeIverksetteRegulering, Regulering>
    fun beregnOgSimuler(request: BeregnRequest): Either<BeregnOgSimulerFeilet, Regulering.OpprettetRegulering>
    fun hentStatus(): List<Regulering>
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>
    fun regulerManuelt(
        reguleringId: UUID,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        fradrag: List<Grunnlag.Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeRegulereManuelt, Regulering.IverksattRegulering>
}
