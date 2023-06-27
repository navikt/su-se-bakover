package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.web.KontrollsamtaleStatusJson.Companion.toJson
import java.time.LocalDate
import java.util.UUID

data class KontrollsamtaleJson(
    val id: UUID,
    val opprettet: Tidspunkt,
    val innkallingsdato: LocalDate,
    val status: KontrollsamtaleStatusJson,
    val frist: LocalDate,
    val dokumentId: UUID?,
    val journalpostIdKontrollnotat: String?,
) {

    companion object {
        fun Kontrollsamtale.toJson(): KontrollsamtaleJson {
            return KontrollsamtaleJson(
                id = this.id,
                opprettet = this.opprettet,
                innkallingsdato = this.innkallingsdato,
                status = this.status.toJson(),
                frist = this.frist,
                dokumentId = this.dokumentId,
                journalpostIdKontrollnotat = this.journalpostIdKontrollnotat?.toString(),
            )
        }

        fun List<Kontrollsamtale>.toJson(): String = serialize(this.map { it.toJson() })
    }
}

enum class KontrollsamtaleStatusJson {
    PLANLAGT_INNKALLING,
    INNKALT,
    GJENNOMFØRT,
    ANNULLERT,
    IKKE_MØTT_INNEN_FRIST,
    ;

    companion object {
        fun Kontrollsamtalestatus.toJson(): KontrollsamtaleStatusJson {
            return when (this) {
                Kontrollsamtalestatus.PLANLAGT_INNKALLING -> PLANLAGT_INNKALLING
                Kontrollsamtalestatus.INNKALT -> INNKALT
                Kontrollsamtalestatus.GJENNOMFØRT -> GJENNOMFØRT
                Kontrollsamtalestatus.ANNULLERT -> ANNULLERT
                Kontrollsamtalestatus.IKKE_MØTT_INNEN_FRIST -> IKKE_MØTT_INNEN_FRIST
            }
        }
    }
}
