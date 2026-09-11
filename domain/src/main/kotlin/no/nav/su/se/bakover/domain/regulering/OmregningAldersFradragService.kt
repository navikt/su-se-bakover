package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.tid.periode.Måned

// Speiler ReguleringAutomatiskService (se samme mappe), men for omregning
// ved alderspensjon-fradragsendring. Manuell trigger (jf. avklaring), så det
// er foreløpig ikke lagt opp til en egen "ForInnsyn"/dryrun-variant slik
// regulering har — kan legges til senere om det trengs.
//
// Implementeres av OmregningAldersFradragServiceImpl
// (service/.../regulering/aldersfradrag/OmregningAldersFradragServiceImpl.kt).

interface OmregningAldersFradragService {
    fun startAutomatiskOmregning(fraOgMedMåned: Måned): List<Either<BleIkkeRegulert, ReguleringOppsummering>>
}

// Egen note (ikke en egen fil i praksis, men samlet her for oversikt):
// ReguleringKjøring.kt har i dag:
//     const val REGULERINGSTYPE_GRUNNBELØP = "GRUNNBELØP"
//     const val REGULERINGSTYPE_ALDERSFRADRAG = "ALDERSFRADRAG"
// Så REGULERINGSTYPE_ALDERSFRADRAG dekker allerede behovet — ingen ny konstant trengs.
