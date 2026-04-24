package no.nav.su.se.bakover.domain.oppgave

import java.time.LocalDate

data class OppgaveV2Config(
    val beskrivelse: String,
    val kategorisering: Kategorisering,
    val bruker: Bruker,
    val aktivDato: LocalDate,
    val fristDato: LocalDate,
    val fordeling: Fordeling? = null,
    val arkivreferanse: Arkivreferanse? = null,
    val tilknyttetApplikasjon: String? = null,
) {
    data class Kategorisering(
        val tema: Kode,
        val oppgavetype: Kode,
        val behandlingstema: Kode?,
        val behandlingstype: Kode,
    )

    data class Kode(
        val kode: String,
    )

    data class Bruker(
        val ident: String,
        val type: Type,
    ) {
        enum class Type {
            PERSON,
        }
    }

    data class Fordeling(
        val enhet: Enhet? = null,
        val medarbeider: Medarbeider? = null,
    ) {
        data class Enhet(
            val nr: String,
        )

        data class Medarbeider(
            val ident: String,
        )
    }

    data class Arkivreferanse(
        val journalpostId: String,
    )
}
