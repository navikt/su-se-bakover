package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import java.time.Duration
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

data class Reguleringsresultat(
    val saksnummer: Saksnummer,
    val behandlingsId: UUID? = null,
    val utfall: Utfall,
    val beskrivelse: String,
    val tidsbrukSekunder: Int? = null,
) {
    enum class Utfall {
        AUTOMATISK,
        MANUELL,
        FEILET,
        MÅ_REVURDERE,
        ALLEREDE_REGULERT,
        IKKE_LOEPENDE,
        AAPEN_REGULERING, // TODO vurder om åpne skal slettes og lages ny
    }
}

fun ReguleringKjøring.logg(): String {
    return """
    Reguleringsresultat
    ------------------------------------------------------------------------------
    Startet: $startTid,
    TidsbrukSekunder: ${Duration.between(startTid, LocalDateTime.now()).seconds}
    ------------------------------------------------------------------------------
    Antall prosesserte saker: $sakerAntall
    sakerIkkeLøpende: ${sakerIkkeLøpende.size},
    sakerAlleredeRegulert: ${sakerAlleredeRegulert.size},
    reguleringerAlleredeÅpen: ${reguleringerAlleredeÅpen.size},
    reguleringerSomFeilet: ${reguleringerSomFeilet.size},
    sakerMåRevurderes: ${sakerMåRevurderes.size},
    reguleringerManuell: ${reguleringerManuell.size},
    reguleringerAutomatisk: ${reguleringerAutomatisk.size},
    ------------------------------------------------------------------------------
    Årsaker til at reguleringene feilet:
    ${
        reguleringerSomFeilet.map { "sak ${it.saksnummer}: ${it.beskrivelse}" }
            .joinToString { "\n              - $it" }
    }
    ------------------------------------------------------------------------------
    Årsaker til revurdering:
    ${
        sakerMåRevurderes.map { "sak ${it.saksnummer}: ${it.beskrivelse}" }
            .joinToString { "\n              - $it" }
    }
    ------------------------------------------------------------------------------
    Årsaker til manuell behandling :
    ${
        reguleringerManuell.map { "sak ${it.saksnummer}: ${it.beskrivelse}" }
            .joinToString { "\n              - $it" }
    }
    ------------------------------------------------------------------------------
    """.trimIndent()
}
