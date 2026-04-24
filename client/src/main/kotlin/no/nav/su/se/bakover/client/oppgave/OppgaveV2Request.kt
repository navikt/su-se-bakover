package no.nav.su.se.bakover.client.oppgave

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class OppgaveV2Request(
    val beskrivelse: String,
    val kategorisering: Kategorisering,
    val bruker: Bruker?,
    val aktivDato: LocalDate?,
    val fristDato: LocalDate?,
    val prioritet: Prioritet?,
    val fordeling: Fordeling?,
    @field:JsonInclude(JsonInclude.Include.NON_EMPTY)
    @field:JsonProperty("nøkkelord")
    val nokkelord: List<String>,
    val arkivreferanse: Arkivreferanse?,
    val tilknyttetSystem: String?,
    val meta: Meta?,
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    internal data class Kategorisering(
        val tema: Kode,
        val oppgavetype: Kode,
        val behandlingstema: Kode?,
        val behandlingstype: Kode?,
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
        val mappe: Mappe?,
        val medarbeider: Medarbeider?,
    ) {
        internal data class Enhet(
            val nr: String,
        )

        internal data class Mappe(
            val id: Long,
        )

        internal data class Medarbeider(
            val navident: String,
        )
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    internal data class Arkivreferanse(
        val saksnr: String?,
        val journalpostId: String?,
    )

    internal enum class Prioritet {
        NORMAL,
        HOY,
        LAV,
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    internal data class Meta(
        val representerer: Representerer?,
        val kommentar: String?,
    ) {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        internal data class Representerer(
            val enhet: Enhet,
        ) {
            internal data class Enhet(
                val nr: String,
            )
        }
    }
}
