package no.nav.su.se.bakover.service.regulering.aldersfradrag

import BleIkkeOmregnetAlder
import OmregningAlderOppsummering
import arrow.core.Either
import no.nav.su.se.bakover.common.tid.periode.Måned

interface OmregningAldersFradragService {
    fun startAutomatiskOmregning(fraOgMedMåned: Måned): List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>>
}
