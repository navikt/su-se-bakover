package no.nav.su.se.bakover.domain.regulering

import java.time.LocalDateTime
import java.util.UUID

data class ReguleringKjøring(
    val id: UUID,
    val aar: Int,
    val type: String,
    val startTid: LocalDateTime,
    val antallProsesserteSaker: Int,
    val antallReguleringerLaget: Int,
    val antallKunneIkkeOpprettes: Int,
    val arsakerReguleringIkkeOpprettet: String,
    val antallAutomatiskeReguleringer: Int,
    val antallSupplementReguleringer: Int,
    val antallReguleringerManuellBehandling: Int,
    val arsakerManuellBehandling: String,
)
