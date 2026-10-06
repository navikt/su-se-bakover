package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.common.domain.sak.Sakstype

// DRAFT — foreslått delt erstatning for den eksisterende (og fortsatt uendrede)
// `internal data class ReguleringTestRun` i ReguleringGrunnbeløpAutomatiskServiceImpl.kt.
//
// Feltene (lagreManuelle, maksAntallSaker, kunSakstype) er ikke grunnbeløp-spesifikke
// i seg selv, så samme klasse bør kunne brukes for både regulering- og
// omregning-dryrun. Foreslått ny, felles plassering — IKKE koblet på noe sted ennå.
//
// ReguleringGrunnbeløpAutomatiskServiceImpl er IKKE endret. Planen (jf. avtale) er å bevise
// dette i OmregningAldersFradragAutomatiskServiceImpl først.

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
    val saksnummer: String? = null,
)
