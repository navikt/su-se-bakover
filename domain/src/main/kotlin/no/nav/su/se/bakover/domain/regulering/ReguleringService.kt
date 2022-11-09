package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.time.LocalDate
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
    object HarÅpenBehandling : KunneIkkeRegulereManuelt()
    data class KunneIkkeFerdigstille(val feil: KunneIkkeFerdigstilleOgIverksette) : KunneIkkeRegulereManuelt()
}

sealed class BeregnOgSimulerFeilet {
    object KunneIkkeSimulere : BeregnOgSimulerFeilet()
}

sealed class KunneIkkeOppretteRegulering {
    object FantIkkeSak : KunneIkkeOppretteRegulering()
    object FørerIkkeTilEnEndring : KunneIkkeOppretteRegulering()
    data class KunneIkkeHenteEllerOppretteRegulering(val feil: Sak.KunneIkkeOppretteEllerOppdatereRegulering) : KunneIkkeOppretteRegulering()
    data class KunneIkkeRegulereAutomatisk(val feil: KunneIkkeFerdigstilleOgIverksette) : KunneIkkeOppretteRegulering()
}

sealed class KunneIkkeAvslutte {
    object FantIkkeRegulering : KunneIkkeAvslutte()
    object UgyldigTilstand : KunneIkkeAvslutte()
}

interface ReguleringService {
    fun startRegulering(startDato: LocalDate): List<Either<KunneIkkeOppretteRegulering, Regulering>>
    fun avslutt(reguleringId: UUID): Either<KunneIkkeAvslutte, Regulering.AvsluttetRegulering>
    fun hentStatus(): List<Pair<Regulering, List<ReguleringMerknad>>>
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>
    fun regulerManuelt(
        reguleringId: UUID,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        fradrag: List<Grunnlag.Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, Regulering.IverksattRegulering>
}
