package no.nav.su.se.bakover.domain.vedtak

import java.time.LocalDate
import java.time.LocalDateTime

data class SakerMedVedtakForFrikort(
    val saker: List<SakMedVedtakForFrikort>,
)

data class SakMedVedtakForFrikort(
    val fnr: String,
    val vedtak: List<VedtakForFrikort>,
)

data class VedtakForFrikort(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val type: String,
    val sakstype: String,
    val opprettet: LocalDateTime,
)
