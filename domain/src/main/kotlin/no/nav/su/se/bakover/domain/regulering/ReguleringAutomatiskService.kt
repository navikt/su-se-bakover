package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak

sealed interface BleIkkeRegulert {
    val saksnummer: Saksnummer

    data class FantIkkeSak(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class HarÅpenReguleringFraFør(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class SkalIkkeRegulere(
        val feil: Sak.KanIkkeRegulere,
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class KunneOppretteRegulering(
        val feil: Sak.KanIkkeRegulere,
        override val saksnummer: Saksnummer,
        val tidsbrukSekunder: Int,
    ) : BleIkkeRegulert

    data class KunneIkkeBehandleAutomatisk(
        val feil: KunneIkkeBehandleRegulering,
        override val saksnummer: Saksnummer,
        val tidsbrukSekunder: Int,
    ) : BleIkkeRegulert

    data class UthentingFradragPesysFeilet(
        val feil: HentingAvEksterneReguleringerFeiletForBruker,
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class UkjentFeil(
        val feil: Throwable,
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert
}

interface ReguleringAutomatiskService {
    fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>>

    fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    )
}
