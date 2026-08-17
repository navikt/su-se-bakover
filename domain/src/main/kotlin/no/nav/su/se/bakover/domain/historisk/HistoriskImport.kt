package no.nav.su.se.bakover.domain.historisk

import java.util.UUID

/**
 * Tapsfri import av data fra supstonad-historisk.
 *
 * Rådataene er bevisst ikke modellert som dagens behandlinger eller vedtak. De skal først projiseres til en
 * versjonert historisk modell når kodeverk og relasjoner i Infotrygd er verifisert.
 */
data class HistoriskImport(
    val id: UUID,
    val status: Status,
    val tabeller: List<Tabell>,
) {
    enum class Status {
        PÅGÅR,
        FULLFØRT,
        FEILET,
    }

    data class Tabell(
        val tabellnavn: String,
        val status: Status,
        val forventetAntall: Long,
        val importertAntall: Long,
        val nesteIterator: String?,
        val nesteSide: Long,
        val kolonner: List<String>,
    )
}

data class NyHistoriskTabellimport(
    val tabellnavn: String,
    val forventetAntall: Long,
    val kolonner: List<String>,
)

data class HistoriskRådataSide(
    val importId: UUID,
    val tabellnavn: String,
    val side: Long,
    val nesteIterator: String?,
    val rader: List<Map<String, String?>>,
)

interface HistoriskImportRepo {
    fun hentPågåendeImport(): HistoriskImport?

    fun opprettImport(tabeller: List<NyHistoriskTabellimport>): HistoriskImport

    /**
     * Lagrer rader og checkpoint atomisk. [HistoriskRådataSide.side] må være lik tabellens neste forventede side.
     */
    fun lagreSide(side: HistoriskRådataSide): HistoriskImport.Tabell

    fun fullførImport(importId: UUID)

    fun markerFeilet(importId: UUID, beskrivelse: String)
}

/**
 * Leser projisert rådata fra en fullført import, partisjonert slik at ikke alt må lastes i minnet samtidig.
 *
 * Referansetabeller (kodeverk og personmapping) er små nok til å holdes i minnet. Transaksjonelle tabeller
 * leses per stønad via [hentVedtakForStønader] og [hentRaderForVedtak].
 */
interface HistoriskRådataLeser {

    /** Alle rader fra en liten referansetabell. Brukes for T_LOPENR_FNR, T_BELOPSTYPE, T_DELYTELSESTYPE, T_KLASSENIVAA. */
    fun hentReferansetabell(importId: UUID, tabellnavn: String): List<Map<String, String?>>

    /** Itererer alle T_STONAD-rader i batches av [batchSize]. */
    fun hentStønaderBatchvis(importId: UUID, batchSize: Int, handler: (List<Map<String, String?>>) -> Unit)

    /** Alle T_VEDTAK-rader med STONAD_ID i [stønadIder]. */
    fun hentVedtakForStønader(importId: UUID, stønadIder: Set<String>): List<Map<String, String?>>

    /** Alle rader fra [tabellnavn] med VEDTAK_ID i [vedtakIder]. */
    fun hentRaderForVedtak(importId: UUID, tabellnavn: String, vedtakIder: Set<String>): List<Map<String, String?>>
}
