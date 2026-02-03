package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.Uføregrunnlag

sealed interface KunneIkkeHenteReguleringsgrunnlag {
    data object FantIkkeRegulering : KunneIkkeHenteReguleringsgrunnlag
    data object FantIkkeGjeldendeVedtaksdata : KunneIkkeHenteReguleringsgrunnlag
}

sealed interface KunneIkkeRegulereManuelt {
    data object FantIkkeRegulering : KunneIkkeRegulereManuelt
    data object SimuleringFeilet : KunneIkkeRegulereManuelt
    data object BeregningFeilet : KunneIkkeRegulereManuelt
    data object AlleredeFerdigstilt : KunneIkkeRegulereManuelt
    data object FantIkkeSak : KunneIkkeRegulereManuelt
    data object StansetYtelseMåStartesFørDenKanReguleres : KunneIkkeRegulereManuelt
    data object ReguleringHarUtdatertePeriode : KunneIkkeRegulereManuelt
    data object AvventerKravgrunnlag : KunneIkkeRegulereManuelt
    data class KunneIkkeFerdigstille(val feil: KunneIkkeFerdigstilleOgIverksette) : KunneIkkeRegulereManuelt
}

sealed interface BeregnOgSimulerFeilet {
    data object KunneIkkeSimulere : BeregnOgSimulerFeilet
}

sealed interface KunneIkkeAvslutte {
    data object FantIkkeRegulering : KunneIkkeAvslutte
    data object UgyldigTilstand : KunneIkkeAvslutte
}

interface ReguleringManuellService {

    fun hentStatusForÅpneManuelleReguleringer(): List<ReguleringSomKreverManuellBehandling>
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>

    fun hentReguleringsgrunnlag(
        reguleringId: ReguleringId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeHenteReguleringsgrunnlag, ReguleringGrunnlagsdata>

    fun beregnReguleringManuelt(
        reguleringId: ReguleringId,
        uføregrunnlag: List<Uføregrunnlag>,
        fradrag: List<Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering>

    fun reguleringTilAttestering(
        reguleringId: ReguleringId,
        uføregrunnlag: List<Uføregrunnlag>,
        fradrag: List<Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering>

    fun godkjennRegulering(
        reguleringId: ReguleringId,
        attestant: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering>

    fun underkjennRegulering(
        reguleringId: ReguleringId,
        attestant: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering>

    fun avslutt(reguleringId: ReguleringId, avsluttetAv: NavIdentBruker): Either<KunneIkkeAvslutte, AvsluttetRegulering>

    fun regulerManuelt(
        reguleringId: ReguleringId,
        uføregrunnlag: List<Uføregrunnlag>,
        fradrag: List<Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering>
}
