package no.nav.su.se.bakover.domain.regulering

import kotlinx.serialization.json.JsonArray
import java.time.LocalDateTime
import java.util.UUID

data class ReguleringKjøring(
    val id: UUID,
    val aar: Int,
    val type: String,
    val dryrun: Boolean,
    val startTid: LocalDateTime,
    val sakerAntall: Int,
    val sakerIkkeLøpende: JsonArray,
    val sakerAlleredeRegulert: JsonArray,
    val sakerMåRevurderes: JsonArray,
    val reguleringerSomFeilet: JsonArray,
    val reguleringerAlleredeÅpen: JsonArray,
    val reguleringerManuell: JsonArray,
    val reguleringerAutomatisk: JsonArray,
) {
    companion object {
        const val REGULERINGSTYPE_GRUNNBELØP = "GRUNNBELØP"
    }
}
