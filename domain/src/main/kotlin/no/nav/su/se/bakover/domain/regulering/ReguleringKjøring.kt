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
    val sakerIkkeLøpende: List<Reguleringsresultat>,
    val sakerAlleredeRegulert: List<Reguleringsresultat>,
    val sakerMåRevurderes: List<Reguleringsresultat>,
    val reguleringerSomFeilet: List<Reguleringsresultat>,
    val reguleringerAlleredeÅpen: List<Reguleringsresultat>,
    val reguleringerManuell: List<Reguleringsresultat>,
    val reguleringerAutomatisk: List<Reguleringsresultat>,
) {
    companion object {
        const val REGULERINGSTYPE_GRUNNBELØP = "GRUNNBELØP"
    }
}
