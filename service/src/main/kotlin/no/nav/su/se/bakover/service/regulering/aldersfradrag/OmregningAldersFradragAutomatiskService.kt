package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import no.nav.su.se.bakover.common.tid.periode.Måned

interface OmregningAldersFradragAutomatiskService {
    fun startAutomatiskOmregning(fraOgMedMåned: Måned): List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>>
    fun startAutomatiskOmregningForInnsyn(fraOgMedMåned: Måned, lagreManuelle: Boolean, maksAntallSaker: Int?, saksnummer: String?): List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>>
}

data class StartAutomatiskOmregningForInnsynCommand(
    val fraOgMedMåned: Måned,
    val lagreManuelle: Boolean = false,
    val maksAntallSaker: Int? = null,
    val saksnummer: String? = null,
)
