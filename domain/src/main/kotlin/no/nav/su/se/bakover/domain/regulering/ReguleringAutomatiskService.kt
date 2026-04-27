package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement

sealed interface KunneIkkeRegulereAutomatisk {
    val saksnummer: Saksnummer

    data class FantIkkeSak(
        override val saksnummer: Saksnummer,
    ) : KunneIkkeRegulereAutomatisk

    // TODO AUTO-REG-26 - vurder om åpne skal slettes og lages ny
    data class HarÅpenReguleringFraFør(
        override val saksnummer: Saksnummer,
    ) : KunneIkkeRegulereAutomatisk

    data class SkalIkkeRegulere(
        val feil: Sak.KanIkkeRegulere,
        override val saksnummer: Saksnummer,
    ) : KunneIkkeRegulereAutomatisk

    data class KunneOppretteRegulering(
        val feil: Sak.KanIkkeRegulere,
        override val saksnummer: Saksnummer,
        val tidsbrukSekunder: Int,
    ) : KunneIkkeRegulereAutomatisk

    data class KunneIkkeBehandleAutomatisk(
        val feil: KunneIkkeBehandleRegulering,
        override val saksnummer: Saksnummer,
        val tidsbrukSekunder: Int,
    ) : KunneIkkeRegulereAutomatisk

    data class UthentingFradragPesysFeilet(
        val feil: HentingAvEksterneReguleringerFeiletForBruker,
        override val saksnummer: Saksnummer,
    ) : KunneIkkeRegulereAutomatisk

    data class UkjentFeil(
        val feil: Throwable,
        override val saksnummer: Saksnummer,
    ) : KunneIkkeRegulereAutomatisk
}

interface ReguleringAutomatiskService {
    fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
        supplement: Reguleringssupplement,
    ): List<Either<KunneIkkeRegulereAutomatisk, ReguleringOppsummering>>

    fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    )
}
