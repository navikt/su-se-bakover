package no.nav.su.se.bakover.domain.personhendelse

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * En folkeregisteridentifikator-hendelse fra PDL som er knyttet til én av våre saker.
 *
 * Vi lagrer bare hvilken sak hendelsen gjaldt – jobben slår opp gjeldende fnr i PDL senere,
 * sammenligner mot sakens fnr og oppdaterer kun dersom de er ulike.
 */
data class Fødselsnummerhendelse(
    val id: UUID,
    val sakId: UUID,
    val opprettet: Tidspunkt,
    val prosessert: Tidspunkt?,
)
