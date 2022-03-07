package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.Reguleringsjobb
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import java.util.UUID
import kotlin.reflect.KClass

data class SakSomKanReguleres(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val reguleringType: ReguleringType,
)

data class SakerSomKanReguleres(
    val saker: List<SakSomKanReguleres>,
)

data class BeregnRequest(
    val behandlingId: UUID,
    val begrunnelse: String?,
)

data class SimulerRequest(
    val behandlingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
)

sealed class KunneIkkeBeregne {
    object BeregningFeilet : KunneIkkeBeregne()
    object FantIkkeRegulering : KunneIkkeBeregne()
    data class UgyldigTilstand(
        val status: KClass<out Regulering>,
    ) : KunneIkkeBeregne()
}

sealed class KunneIkkeSimulere {
    object FantIkkeRegulering : KunneIkkeSimulere()
    object FantIkkeBeregning : KunneIkkeSimulere()
    object SimuleringFeilet : KunneIkkeSimulere()
    data class UgyldigTilstand(
        val status: KClass<out Regulering>,
    ) : KunneIkkeSimulere()
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
    object ReguleringErAlleredeIverksatt : KunneIkkeOppretteRegulering()
}

sealed class KunneIkkeHenteGjeldendeVedtaksdata {
    object FantIngenVedtak : KunneIkkeHenteGjeldendeVedtaksdata()
    object FantIkkeSak : KunneIkkeHenteGjeldendeVedtaksdata()
    object UgyldigPeriode : KunneIkkeHenteGjeldendeVedtaksdata()
    object TidslinjeForVedtakErIkkeKontinuerlig : KunneIkkeHenteGjeldendeVedtaksdata()
    object GrunnlagErIkkeKonsistent : KunneIkkeHenteGjeldendeVedtaksdata()
}

sealed class KunneIkkeStarteRegulering {
    object DetFinnesAlleredeEnRegulering : KunneIkkeStarteRegulering()
}

sealed class KunneIkkeFortsettRegulering {
    object DetFinnesAlleredeEnRegulering : KunneIkkeFortsettRegulering()
}

object KunneIkkeUtbetale

interface ReguleringService {
    fun startRegulering(reguleringsjobb: Reguleringsjobb): Either<KunneIkkeStarteRegulering, Unit>
    fun fortsettRegulering(): Either<KunneIkkeFortsettRegulering, Unit>
    fun leggTilFradrag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradrag, Regulering>
    fun iverksett(reguleringId: UUID): Either<KunneIkkeIverksetteRegulering, Regulering>
    fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, Regulering.OpprettetRegulering>
    fun simuler(request: SimulerRequest): Either<KunneIkkeSimulere, Regulering.OpprettetRegulering>
    fun hentStatus(reguleringsjobb: Reguleringsjobb): List<Regulering>
    fun hentSakerMedÅpneBehandlinger(): List<Saksnummer>
}
