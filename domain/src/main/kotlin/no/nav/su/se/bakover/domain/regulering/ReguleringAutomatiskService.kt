package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement

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

interface ReguleringAutomatiskService {
    fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
        supplement: Reguleringssupplement,
    ): List<Either<KunneIkkeOppretteRegulering, Regulering>>

    fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    )

    fun oppdaterReguleringerMedSupplement(supplement: Reguleringssupplement)
}
