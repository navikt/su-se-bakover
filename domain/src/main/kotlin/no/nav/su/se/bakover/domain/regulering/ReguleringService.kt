package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.Uføregrunnlag

sealed interface KunneIkkeFerdigstilleOgIverksette {
    data object KunneIkkeBeregne : KunneIkkeFerdigstilleOgIverksette
    data object KunneIkkeSimulere : KunneIkkeFerdigstilleOgIverksette
    data class KunneIkkeUtbetale(val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon) : KunneIkkeFerdigstilleOgIverksette
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

sealed interface KunneIkkeOppretteRegulering {
    data object FantIkkeSak : KunneIkkeOppretteRegulering
    data object FørerIkkeTilEnEndring : KunneIkkeOppretteRegulering
    data class KunneIkkeHenteEllerOppretteRegulering(
        val feil: Sak.KunneIkkeOppretteEllerOppdatereRegulering,
    ) : KunneIkkeOppretteRegulering

    data class KunneIkkeRegulereAutomatisk(
        val feil: KunneIkkeFerdigstilleOgIverksette,
    ) : KunneIkkeOppretteRegulering

    data object UkjentFeil : KunneIkkeOppretteRegulering
}

sealed interface KunneIkkeAvslutte {
    data object FantIkkeRegulering : KunneIkkeAvslutte
    data object UgyldigTilstand : KunneIkkeAvslutte
}

interface ReguleringService {
    fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
        supplement: Reguleringssupplement,
    ): List<Either<KunneIkkeOppretteRegulering, Regulering>>

    fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    )

    fun avslutt(reguleringId: ReguleringId, avsluttetAv: NavIdentBruker): Either<KunneIkkeAvslutte, AvsluttetRegulering>
    fun hentStatusForÅpneManuelleReguleringer(): List<ReguleringSomKreverManuellBehandling>
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>
    fun regulerManuelt(
        reguleringId: ReguleringId,
        uføregrunnlag: List<Uføregrunnlag>,
        fradrag: List<Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering>

    fun oppdaterReguleringerMedSupplement(supplement: Reguleringssupplement)
}
