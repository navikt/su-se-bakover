package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.Uføregrunnlag
import java.time.Clock

sealed interface KunneIkkeHenteReguleringsgrunnlag {
    data object FantIkkeRegulering : KunneIkkeHenteReguleringsgrunnlag
    data object FantIkkeGjeldendeVedtaksdata : KunneIkkeHenteReguleringsgrunnlag
}

sealed interface KunneIkkeRegulereManuelt {
    interface Beregne : KunneIkkeRegulereManuelt {
        data object ReguleringstypeAutomatisk : Beregne
        data object IkkeUnderBehandling : Beregne
        data object FeilMedBeregningsgrunnlag : Beregne
    }
    data object FeilTilstandForAttestering : KunneIkkeRegulereManuelt
    data object FeilTilstandForIverksettelse : KunneIkkeRegulereManuelt
    data object FeilTilstandForUnderkjennelse : KunneIkkeRegulereManuelt
    data object FantIkkeRegulering : KunneIkkeRegulereManuelt
    data object BeregningOgSimuleringFeilet : KunneIkkeRegulereManuelt
    data object AlleredeFerdigstilt : KunneIkkeRegulereManuelt
    data object FantIkkeSak : KunneIkkeRegulereManuelt
    data object StansetYtelseMåStartesFørDenKanReguleres : KunneIkkeRegulereManuelt
    data object ReguleringHarUtdatertePeriode : KunneIkkeRegulereManuelt
    data object AvventerKravgrunnlag : KunneIkkeRegulereManuelt
    data class KunneIkkeFerdigstille(val feil: KunneIkkeFerdigstilleOgIverksette) : KunneIkkeRegulereManuelt
}

sealed interface KunneIkkeAvslutte {
    data object FantIkkeRegulering : KunneIkkeAvslutte
    data object UgyldigTilstand : KunneIkkeAvslutte
}

interface ReguleringManuellService {

    fun hentStatusForÅpneManuelleReguleringer(): List<ReguleringSomKreverManuellBehandling>
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>

    fun hentRegulering(
        reguleringId: ReguleringId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeHenteReguleringsgrunnlag, ManuellReguleringVisning>

    fun beregnReguleringManuelt(
        reguleringId: ReguleringId,
        uføregrunnlag: List<Uføregrunnlag>,
        fradrag: List<Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, ReguleringUnderBehandling.BeregnetRegulering>

    fun reguleringTilAttestering(
        reguleringId: ReguleringId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, ReguleringUnderBehandling.TilAttestering>

    fun godkjennRegulering(
        reguleringId: ReguleringId,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering>

    fun underkjennRegulering(
        reguleringId: ReguleringId,
        attestant: NavIdentBruker.Attestant,
        kommentar: String,
        clock: Clock,
    ): Either<KunneIkkeRegulereManuelt, ReguleringUnderBehandling.BeregnetRegulering>

    fun avslutt(reguleringId: ReguleringId, avsluttetAv: NavIdentBruker): Either<KunneIkkeAvslutte, AvsluttetRegulering>

    fun regulerManuelt(
        reguleringId: ReguleringId,
        uføregrunnlag: List<Uføregrunnlag>,
        fradrag: List<Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering>
}
