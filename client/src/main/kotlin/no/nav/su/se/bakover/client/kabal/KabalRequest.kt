package no.nav.su.se.bakover.client.kabal

import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Docs:
 *  https://confluence.adeo.no/pages/viewpage.action?pageId=441059973
 *  https://confluence.adeo.no/display/FIP/Kabaldata
 *
 *
 * @param avsenderSaksbehandlerIdent Ident til saksbehandler
 * @param dvhReferanse Intern referanse som Kabal bruker når de leverer statistikk
 * @param fagsak Informasjon om saken/klagen i systemet som kaller på Kabal.
 * @param hjemler Liste med hjemler. Disse vises i Kabal.
 * @param innsendtTilNav Dato for når klagen ble innsendt.
 * @param mottattFoersteinstans Dato for når klagen registrerades på første instans
 * @param kilde Kode for kildesystemet. Brukes til filtrering på Kafka når vedtaket sendes fra Kabal.
 * @param kildeReferanse Intern referanse på klagen
 * @param klager Id til Klager.
 * @param tilknyttedeJournalposter Relevante journalposter til klagen. Disse vises i Kabal.
 * @param oversendtKaDato Kan settes dersom denne saken har blitt sendt til Gosys og derfor har fristen begynt å løpe.
 * @param innsynUrl Url tilbake til kildesystem for innsyn i sak
 * @param type Gyldige verdier er "KLAGE" i både prod og dev
 * @param ytelse Ytelsekode
 * */
internal data class KabalRequest(
    val avsenderEnhet: String = "4815",
    val avsenderSaksbehandlerIdent: String,
    val dvhReferanse: String?,
    val fagsak: Fagsak?,
    val hjemler: List<Hjemler>?,
    val innsendtTilNav: LocalDate,
    val mottattFoersteinstans: LocalDate,
    val kilde: String = "SUPSTONAD",
    val kildeReferanse: String,
    val klager: Klager,
    val tilknyttedeJournalposter: List<TilknyttedeJournalposter>,
    val kommentar: String? = null,
    val frist: LocalDate? = null,
    val sakenGjelder: SakenGjelder? = null,
    val oversendtKaDato: LocalDateTime? = null,
    val innsynUrl: String? = null,
    val type: String = "KLAGE",
    /*
    val ytelse: String = "SUP_UFF"
    */
    val ytelse: String = "OMS_OMP"
) {
    data class Klager(val id: PartId)
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
            FORVALTNINGSLOVEN("FORVALTNINGSLOVEN"),
            SUPPLERENDE_STONAD("LOV_OM_SUPPLERENDE_STØNAD");

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
