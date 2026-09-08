package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype

// DRAFT — foreslått delt erstatning for den eksisterende (og fortsatt uendrede)
// `internal data class ReguleringTestRun` i ReguleringAutomatiskServiceImpl.kt.
//
// Feltene (lagreManuelle, maksAntallSaker, kunSakstype) er ikke grunnbeløp-spesifikke
// i seg selv, så samme klasse bør kunne brukes for både regulering- og
// omregning-dryrun. Foreslått ny, felles plassering — IKKE koblet på noe sted ennå.
//
// ReguleringAutomatiskServiceImpl er IKKE endret. Planen (jf. avtale) er å bevise
// dette i OmregningAldersFradragServiceImpl først.

/**
 * Konfigurasjon for dryrun/innsyn-kjøringer, felles for både automatisk regulering
 * og automatisk omregning.
 *
 * @param lagreManuelle om manuelle reguleringer/omregninger skal lagres under en dryrun
 * @param maksAntallSaker begrens antall saker som behandles (kun ved dryrun)
 * @param kunSakstype begrens til én sakstype (kun ved dryrun)
 */
internal data class AutomatiskTestRun(
    val lagreManuelle: Boolean = false,
    val maksAntallSaker: Int? = null,
    val kunSakstype: Sakstype? = null,
) {
    /**
     * Avgjør om en manuell regulering/omregning skal lagres under en dry run
     * (test-/innsynskjøring).
     *
     * @return true bare når vi ikke kjører i produksjon, manuelle
     *   reguleringer/omregninger skal lagres, og den gitte reguleringen er manuell
     */
    fun lagreManuelleUnderDryRun(regulering: Regulering) =
        ApplicationConfig.isNotProd() && lagreManuelle && regulering.reguleringstype is Reguleringstype.MANUELL
}
