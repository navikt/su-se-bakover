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
