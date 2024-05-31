package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.web.KontrollsamtaleStatusJson.Companion.toJson
import java.time.LocalDate

private data class KontrollsamtaleJson(
    val id: String,
    val opprettet: Tidspunkt,
    val innkallingsdato: LocalDate,
    val status: KontrollsamtaleStatusJson,
    val frist: LocalDate,
    val dokumentId: String?,
    val journalpostIdKontrollnotat: String?,
    val kanOppdatereInnkallingsmåned: Boolean,
    val lovligeStatusovergangerForSaksbehandler: List<KontrollsamtaleStatusJson>,
)

internal fun Kontrollsamtale.toJson(): String {
    return KontrollsamtaleJson(
        id = this.id.toString(),
        opprettet = this.opprettet,
        innkallingsdato = this.innkallingsdato,
        status = this.status.toJson(),
        frist = this.frist,
        dokumentId = this.dokumentId?.toString(),
        journalpostIdKontrollnotat = this.journalpostIdKontrollnotat?.toString(),
        kanOppdatereInnkallingsmåned = this.kanOppdatereInnkallingsmåned(),
        lovligeStatusovergangerForSaksbehandler = this.lovligeOvergangerForSaksbehandler().map { it.toJson() },
    ).let { serialize(it) }
}

internal fun Kontrollsamtaler.toJson(): String = """
    [${this.joinToString(",") { it.toJson() }}]
""".trimIndent()

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
