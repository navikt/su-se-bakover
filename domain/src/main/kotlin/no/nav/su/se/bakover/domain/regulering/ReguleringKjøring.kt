package no.nav.su.se.bakover.domain.regulering

import java.time.LocalDateTime
import java.util.UUID

data class ReguleringKjøring(
    val id: UUID,
    val aar: Int,
    val type: String,
    val dryrun: Boolean,
    val startTid: LocalDateTime,
    val sakerAntall: Int,
    val sakerIkkeLøpende: List<String>,
    val sakerAlleredeRegulert: List<String>,
    val sakerMåRevurderes: List<String>,
    val reguleringerSomFeilet: List<String>,
    val reguleringerAlleredeÅpen: List<String>,
    val reguleringerManuell: List<String>,
    val reguleringerAutomatisk: List<String>,
) {
    companion object {
        const val REGULERINGSTYPE_GRUNNBELØP = "GRUNNBELØP"
    }
}
