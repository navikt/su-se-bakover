package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.util.UUID

sealed class KunneIkkeFerdigstilleOgIverksette {
    object KunneIkkeBeregne : KunneIkkeFerdigstilleOgIverksette()
    object KunneIkkeSimulere : KunneIkkeFerdigstilleOgIverksette()
    object KunneIkkeUtbetale : KunneIkkeFerdigstilleOgIverksette()
    object KanIkkeAutomatiskRegulereSomFørerTilFeilutbetaling : KunneIkkeFerdigstilleOgIverksette()
}

sealed class KunneIkkeRegulereManuelt {
    object FantIkkeRegulering : KunneIkkeRegulereManuelt()
    object SimuleringFeilet : KunneIkkeRegulereManuelt()
    object BeregningFeilet : KunneIkkeRegulereManuelt()
    object AlleredeFerdigstilt : KunneIkkeRegulereManuelt()
    object FantIkkeSak : KunneIkkeRegulereManuelt()
    object StansetYtelseMåStartesFørDenKanReguleres : KunneIkkeRegulereManuelt()
    object AvventerKravgrunnlag : KunneIkkeRegulereManuelt()
    object HarPågåendeEllerBehovForAvkorting : KunneIkkeRegulereManuelt()
    data class KunneIkkeFerdigstille(val feil: KunneIkkeFerdigstilleOgIverksette) : KunneIkkeRegulereManuelt()
}

sealed class BeregnOgSimulerFeilet {
    object KunneIkkeSimulere : BeregnOgSimulerFeilet()
}

sealed interface KunneIkkeOppretteRegulering {
    object FantIkkeSak : KunneIkkeOppretteRegulering
    object FørerIkkeTilEnEndring : KunneIkkeOppretteRegulering
    data class KunneIkkeHenteEllerOppretteRegulering(
        val feil: Sak.KunneIkkeOppretteEllerOppdatereRegulering,
    ) : KunneIkkeOppretteRegulering

    data class KunneIkkeRegulereAutomatisk(
        val feil: KunneIkkeFerdigstilleOgIverksette,
    ) : KunneIkkeOppretteRegulering

    object UkjentFeil : KunneIkkeOppretteRegulering
}

sealed class KunneIkkeAvslutte {
    object FantIkkeRegulering : KunneIkkeAvslutte()
    object UgyldigTilstand : KunneIkkeAvslutte()
}

interface ReguleringService {
    fun startAutomatiskRegulering(fraOgMedMåned: Måned): List<Either<KunneIkkeOppretteRegulering, Regulering>>

    fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    )

    fun avslutt(reguleringId: UUID): Either<KunneIkkeAvslutte, AvsluttetRegulering>
    fun hentStatus(): List<Pair<Regulering, List<ReguleringMerknad>>>
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>
    fun regulerManuelt(
        reguleringId: UUID,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        fradrag: List<Grunnlag.Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering>
}
