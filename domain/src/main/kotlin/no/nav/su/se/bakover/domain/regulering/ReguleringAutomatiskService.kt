package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement

sealed interface KunneIkkeRegulereAutomatisk {
    data object FantIkkeSak : KunneIkkeRegulereAutomatisk
    data object FørerIkkeTilEnEndring : KunneIkkeRegulereAutomatisk
    data object HarÅpenReguleringFraFør : KunneIkkeRegulereAutomatisk

    data class KunneIkkeHenteEllerOppretteRegulering(
        val feil: Sak.KanIkkeRegulere,
    ) : KunneIkkeRegulereAutomatisk

    data class KunneIkkeBehandleAutomatisk(
        val feil: KunneIkkeBehandleRegulering,
    ) : KunneIkkeRegulereAutomatisk

    data class UthentingFradragPesysFeilet(
        val feil: HentingAvEksterneReguleringerFeiletForBruker,
    ) : KunneIkkeRegulereAutomatisk

    data object UkjentFeil : KunneIkkeRegulereAutomatisk
}

interface ReguleringAutomatiskService {
    fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
        supplement: Reguleringssupplement,
    ): List<Either<KunneIkkeRegulereAutomatisk, ReguleringOppsummering>>

    fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    )

    fun oppdaterReguleringerMedSupplement(supplement: Reguleringssupplement)
}
