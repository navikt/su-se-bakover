package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import java.time.LocalDate
import java.util.UUID

data class BeregnRequest(
    val behandlingId: UUID,
    val begrunnelse: String?,
)

sealed class KunneIkkeRegulereAutomatiskt {
    object KunneIkkeBeregne : KunneIkkeRegulereAutomatiskt()
    object KunneIkkeSimulere : KunneIkkeRegulereAutomatiskt()
    object KunneIkkeUtbetale : KunneIkkeRegulereAutomatiskt()
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
    object FantIkkeRegulering : KunneIkkeOppretteRegulering()
    object FantIngenVedtak : KunneIkkeOppretteRegulering()
    object UgyldigPeriode : KunneIkkeOppretteRegulering()
    object TidslinjeForVedtakErIkkeKontinuerlig : KunneIkkeOppretteRegulering()
    object GrunnlagErIkkeKonsistent : KunneIkkeOppretteRegulering()
    object KunneIkkeLageFradragsgrunnlag : KunneIkkeOppretteRegulering()
}

sealed class KunneIkkeHenteGjeldendeVedtaksdata {
    object FantIngenVedtak : KunneIkkeHenteGjeldendeVedtaksdata()
    object FantIkkeSak : KunneIkkeHenteGjeldendeVedtaksdata()
    object UgyldigPeriode : KunneIkkeHenteGjeldendeVedtaksdata()
    object TidslinjeForVedtakErIkkeKontinuerlig : KunneIkkeHenteGjeldendeVedtaksdata()
    data class GrunnlagErIkkeKonsistent(val gjeldendeVedtaksdata: GjeldendeVedtaksdata) : KunneIkkeHenteGjeldendeVedtaksdata()
}

object KunneIkkeUtbetale

interface ReguleringService {
    fun startRegulering(startDato: LocalDate): List<Either<KunneIkkeOppretteRegulering, Regulering>>
    fun leggTilFradrag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradrag, Regulering>
    fun iverksett(reguleringId: UUID): Either<KunneIkkeIverksetteRegulering, Regulering>
    fun beregnOgSimuler(request: BeregnRequest): Either<BeregnOgSimulerFeilet, Regulering.OpprettetRegulering>
    fun hentStatus(): List<Regulering>
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>
}
