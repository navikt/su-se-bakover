package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.KunneIkkeBehandleRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering

interface OmregningAldersFradragAutomatiskService {
    fun startAutomatiskOmregning(fraOgMedMåned: Måned): List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>>
    fun startAutomatiskOmregningForInnsyn(fraOgMedMåned: Måned, lagreManuelle: Boolean, maksAntallSaker: Int?, kunSakstype: Sakstype?): List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>>
}

sealed interface BleIkkeOmregnetAlder {
    val saksnummer: Saksnummer

    sealed interface TrengerIkkeOmregne : BleIkkeOmregnetAlder {
        data class IkkeLøpendeSak(
            override val saksnummer: Saksnummer,
        ) : TrengerIkkeOmregne
    }

    data class UthentingFradragEksterntFeilet(
        val feil: HentingAvEksterneReguleringerFeiletForBruker,
        override val saksnummer: Saksnummer,
    ) : BleIkkeOmregnetAlder

    data class KunneIkkeBehandleAutomatisk(
        val feil: KunneIkkeBehandleRegulering,
        override val saksnummer: Saksnummer,
    ) : BleIkkeOmregnetAlder

    data class HarIkkeAlderspensjonFradrag(
        override val saksnummer: Saksnummer,
    ) : BleIkkeOmregnetAlder
}

data class StartAutomatiskOmregningForInnsynCommand(
    val fraOgMedMåned: Måned,
    val lagreManuelle: Boolean = false,
    val maksAntallSaker: Int? = null,
    val kunSakstype: Sakstype? = null,
)
