package no.nav.su.se.bakover.client.kabal

import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Docs:
 *  https://confluence.adeo.no/pages/viewpage.action?pageId=441059973
 *  https://confluence.adeo.no/display/FIP/Kabaldata
 * */
internal data class KabalRequest(
    val avsenderEnhet: String = "4815",
    val avsenderSaksbehandlerIdent: String,
    val dvhReferanse: String?,
    val fagsak: Fagsak?,
    val hjemler: List<Hjemler>?,
    val innsendtTilNav: LocalDate,
    val mottattFoersteinstans: LocalDate,
    val kilde: String = "su-se-bakover",
    val kildeReferanse: String,
    val klager: Klager,
    val tilknyttedeJournalposter: List<TilknyttedeJournalposter>,
    val kommentar: String? = null,
    val frist: LocalDate? = null,
    val sakenGjelder: SakenGjelder? = null,
    val oversendtKaDato: LocalDateTime? = null,
    val innsynUrl: String? = null,
    val type: String = "KLAGE",
    val ytelse: String, // todo ai: få ny ytelsekode
) {
    data class Klager(val id: PartId, val skalKlagerMottaKopi: Boolean)
    data class SakenGjelder(val id: PartId, val skalMottaKopi: Boolean)

    data class TilknyttedeJournalposter(val journalpostId: JournalpostId, val type: Type) {
        enum class Type(private val verdi: String) {
            ANNET("ANNET"),
            BRUKERS_KLAGE("BRUKERS_KLAGE"),
            OPPRINNELIG_VEDTAK("OPPRINNELIG_VEDTAK"),
            OVERSENDELSESBREV("OVERSENDELSESBREV");

            override fun toString(): String = this.verdi
        }
    }

    data class Hjemler(
        val kapittel: Int?,
        val lov: Lov,
        val paragraf: Int?
    ) {
        enum class Lov(private val verdi: String) {
            FOLKETRYGDLOVEN("FOLKETRYGDLOVEN"),
            FORVALTNINGSLOVEN("FORVALTNINGSLOVEN");

            override fun toString(): String = this.verdi
        }
    }

    data class Fagsak(
        val fagsakId: String,
        val fagsystem: String = "SUPSTONAD",
    )

    data class PartId(
        val type: String = "PERSON",
        val verdi: String,
    )
}
