package no.nav.su.se.bakover.domain.historisk

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * Tapsfri import av data fra supstonad-historisk.
 *
 * Rådataene er bevisst ikke modellert som dagens behandlinger eller vedtak. De kan projiseres til en versjonert
 * historisk lesemodell, mens snapshotet forblir den tapsfrie kilden for felter og fagregler som ikke er avklart.
 */
data class HistoriskImport(
    val id: UUID,
    val status: Status,
    val opprettet: no.nav.su.se.bakover.common.tid.Tidspunkt,
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
        /** Cursor fra forrige kilderespons som skal sendes i neste request. */
        val nesteIterator: String?,
        /** Nullbasert, lokal indeks for neste side/checkpoint som skal lagres. Ikke en iterator fra kilden. */
        val nesteSide: Long,
        val kolonner: List<String>,
    )
}

data class NyHistoriskTabellimport(
    val tabellnavn: String,
    val forventetAntall: Long,
    val kolonner: List<String>,
) {
    init {
        require(kolonner.isNotEmpty()) { "$tabellnavn mangler kolonner" }
        require(kolonner.distinct().size == kolonner.size) { "$tabellnavn inneholder duplikate kolonnenavn" }
    }
}

data class HistoriskRådataSide(
    val importId: UUID,
    val tabellnavn: String,
    /** Nullbasert, lokal lagringsindeks. En tom terminalside får også en indeks. */
    val side: Long,
    /** Cursor returnert av kilden. Kan være uendret på en tom terminalside. */
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

    fun hentAlleImporter(): List<HistoriskImportOversikt>

    /**
     * Sletter en fullført eller feilet import med alle tilhørende tabellrader.
     * En pågående import kan ikke slettes. Utfallet skilles via [SlettImportResultat].
     */
    fun slettImport(importId: UUID): SlettImportResultat
}

enum class SlettImportResultat {
    SLETTET,
    IKKE_FUNNET,
    PÅGÅR,
}

data class HistoriskImportOversikt(
    val id: UUID,
    val status: HistoriskImport.Status,
    val opprettet: Tidspunkt,
    val fullført: Tidspunkt?,
    val feilbeskrivelse: String?,
    val tabeller: List<HistoriskImportTabellOversikt>,
) {
    val totaltForventetAntall: Long get() = tabeller.sumOf { it.forventetAntall }
    val totaltImportertAntall: Long get() = tabeller.sumOf { it.importertAntall }
}

data class HistoriskImportTabellOversikt(
    val tabellnavn: String,
    val status: HistoriskImport.Status,
    val forventetAntall: Long,
    val importertAntall: Long,
)

/**
 * Leser projisert rådata fra en fullført import, partisjonert slik at ikke alt må lastes i minnet samtidig.
 *
 * Små kodeverkstabeller holdes i minnet. Personmappingen slås opp per batch, og transaksjonelle tabeller leses
 * per stønad via [hentVedtakForStønader] og [hentRaderForVedtak].
 */
interface HistoriskRådataLeser {

    /** Sjekker at importen er fullført. Kaster [IllegalStateException] dersom importen ikke finnes eller ikke er fullført. */
    fun verifiserFullførtImport(importId: UUID)

    /** Alle rader fra en liten referansetabell. Brukes for T_BELOPSTYPE, T_DELYTELSESTYPE, T_KLASSENIVAA. */
    fun hentReferansetabell(importId: UUID, tabellnavn: String): List<Map<String, String?>>

    /**
     * Leser T_STONAD-rader sekvensielt i batches av [batchSize].
     */
    fun hentStønaderBatchvis(
        importId: UUID,
        batchSize: Int,
        maksAntallRader: Int? = null,
    ): Sequence<List<Map<String, String?>>>

    /** Alle T_VEDTAK-rader med STONAD_ID i [stønadIder]. */
    fun hentVedtakForStønader(importId: UUID, stønadIder: Set<String>): List<Map<String, String?>>

    /** Alle rader fra [tabellnavn] med VEDTAK_ID i [vedtakIder]. */
    fun hentRaderForVedtak(importId: UUID, tabellnavn: String, vedtakIder: Set<String>): List<Map<String, String?>>

    /**
     * T_LOPENR_FNR-rader for de gitte [lopenummer]-verdiene, indeksert på PERSON_LOPENR.
     * Brukes for on-demand oppslag per batch slik at hele den 8M+ store tabellen aldri lastes i minnet.
     */
    fun hentPersonerForLopenummer(importId: UUID, lopenummer: Set<String>): Map<String, Map<String, String?>>
}
