package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.common.domain.sak.Sakstype
/**
 * Konfigurasjon for dryrun/innsyn-kjøringer, felles for både automatisk regulering
 * og automatisk omregning.
 *
 * @param lagreManuelle om manuelle reguleringer/omregninger skal lagres under en dryrun
 * @param maksAntallSaker begrens antall saker som behandles (kun ved dryrun)
 * @param kunSakstype begrens til én sakstype (kun ved dryrun)
 */
internal data class AutomatiskTestRunOmregning(
    val maksAntallSaker: Int? = null,
    val kunSakstype: Sakstype? = null,
    val saksnummer: String? = null,
)
