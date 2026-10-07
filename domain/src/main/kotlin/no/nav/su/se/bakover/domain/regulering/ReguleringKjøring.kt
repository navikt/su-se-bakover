package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

sealed interface ReguleringKjøring {
    val id: UUID
    val aar: Int
    val type: Type
    val dryrun: Boolean
    val startTid: LocalDateTime
    val sakerAntall: Int
    val sakerIkkeLøpende: List<Reguleringsresultat>
    val reguleringerSomFeilet: List<Reguleringsresultat>
    val reguleringerAlleredeÅpen: List<Reguleringsresultat>
    val reguleringerManuell: List<Reguleringsresultat>

    data class Grunnbeløp(
        override val id: UUID,
        override val aar: Int,
        override val dryrun: Boolean,
        override val startTid: LocalDateTime,
        override val sakerAntall: Int,
        override val sakerIkkeLøpende: List<Reguleringsresultat>,
        override val reguleringerSomFeilet: List<Reguleringsresultat>,
        override val reguleringerAlleredeÅpen: List<Reguleringsresultat>,
        override val reguleringerManuell: List<Reguleringsresultat>,

        val sakerAlleredeRegulert: List<Reguleringsresultat>,
        val sakerMåRevurderes: List<Reguleringsresultat>,
        val reguleringerAutomatisk: List<Reguleringsresultat>,
    ) : ReguleringKjøring {
        override val type: Type = Type.GRUNNBELØP
    }

    data class Aldersfradrag(
        override val id: UUID,
        override val aar: Int,
        override val dryrun: Boolean,
        override val startTid: LocalDateTime,
        override val sakerAntall: Int,
        override val sakerIkkeLøpende: List<Reguleringsresultat>,
        override val reguleringerSomFeilet: List<Reguleringsresultat>,
        override val reguleringerAlleredeÅpen: List<Reguleringsresultat>,
        override val reguleringerManuell: List<Reguleringsresultat>,

        val skalIkkeOmregnes: List<Reguleringsresultat>,

    ) : ReguleringKjøring {
        override val type: Type = Type.ALDERSFRADRAG
    }

    enum class Type {
        GRUNNBELØP,
        ALDERSFRADRAG,
    }
}

data class Reguleringsresultat(
    val saksnummer: Saksnummer,
    val behandlingsId: UUID? = null,
    val utfall: Utfall,
    val beskrivelse: String,
) {
    enum class Utfall {
        AUTOMATISK,
        MANUELL,
        FEILET,
        MÅ_REVURDERE,
        ALLEREDE_REGULERT,
        IKKE_LOEPENDE,
        AAPEN_REGULERING,
        SKAL_IKKE_OMREGNES,
    }
}

fun ReguleringKjøring.logg(): String {
    val sakerAlleredeRegulert = if (this is ReguleringKjøring.Grunnbeløp) this.sakerAlleredeRegulert else null
    val sakerMåRevurderes = if (this is ReguleringKjøring.Grunnbeløp) this.sakerMåRevurderes else null
    val reguleringerAutomatisk = if (this is ReguleringKjøring.Grunnbeløp) this.reguleringerAutomatisk else null

    val skalIkkeOmregnes = if (this is ReguleringKjøring.Aldersfradrag) this.skalIkkeOmregnes else null

    return """
    Reguleringsresultat
    ------------------------------------------------------------------------------
    Startet: $startTid,
    TidsbrukSekunder: ${Duration.between(startTid, LocalDateTime.now()).seconds}
    ------------------------------------------------------------------------------
    Antall prosesserte saker: $sakerAntall
    sakerIkkeLøpende: ${sakerIkkeLøpende.size},
    ${sakerAlleredeRegulert?.let { "sakerAlleredeRegulert: ${it.size}" }},
    reguleringerAlleredeÅpen: ${reguleringerAlleredeÅpen.size},
    reguleringerSomFeilet: ${reguleringerSomFeilet.size},
    ${sakerMåRevurderes?.let { "sakerMåRevurderes: ${it.size}" }},
    reguleringerManuell: ${reguleringerManuell.size},
    ${reguleringerAutomatisk?.let { "reguleringerAutomatisk: ${it.size}" }},
    ${skalIkkeOmregnes?.let { "skalIkkeOmregnes: ${it.size}" }},
    ------------------------------------------------------------------------------
    ${
        sakerMåRevurderes?.let {
            """
        Årsaker til revurdering:
    ${sakerMåRevurderes.map { "sak ${it.saksnummer}: ${it.beskrivelse}" }.joinToString { "\n              - $it" }}
    ------------------------------------------------------------------------------
            """.trimIndent()
        }
    }
    Årsaker til manuell behandling :
    ${reguleringerManuell.map { "sak ${it.saksnummer}: ${it.beskrivelse}" }.joinToString { "\n              - $it" }}
    ------------------------------------------------------------------------------
        Årsaker til at reguleringene feilet:
    ${reguleringerSomFeilet.map { "sak ${it.saksnummer}" }.joinToString { "\n              - $it" }}
    ------------------------------------------------------------------------------
    """.trimIndent()
}
