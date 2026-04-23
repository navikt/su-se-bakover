package no.nav.su.se.bakover.client.oppgave

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class OppgaveV2Request(
    val beskrivelse: String,
    val kategorisering: Kategorisering,
    val bruker: Bruker,
    val aktivDato: LocalDate,
    val fristDato: LocalDate,
    val fordeling: Fordeling?,
    val arkivreferanse: Arkivreferanse?,
    val tilknyttetApplikasjon: String?,
    val meta: Meta?,
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    internal data class Kategorisering(
        val tema: Kode,
        val oppgavetype: Kode,
        val behandlingstema: Kode?,
        val behandlingstype: Kode,
    )

    internal data class Kode(
        val kode: String,
    )

    internal data class Bruker(
        val ident: String,
        val type: Type,
    ) {
        internal enum class Type {
            PERSON,
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    internal data class Fordeling(
        val enhet: Enhet?,
        val medarbeider: Medarbeider?,
    ) {
        internal data class Enhet(
            val nr: String,
        )

        internal data class Medarbeider(
            val ident: String,
        )
    }

    internal data class Arkivreferanse(
        val journalpostId: String,
    )

    internal data class Meta(
        val representerer: Representerer,
    ) {
        internal data class Representerer(
            val enhet: Enhet,
        ) {
            internal data class Enhet(
                val nr: String,
            )
        }
    }
}
