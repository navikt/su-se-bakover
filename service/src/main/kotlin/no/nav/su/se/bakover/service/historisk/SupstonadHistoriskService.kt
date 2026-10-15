package no.nav.su.se.bakover.service.historisk

import arrow.core.Either
import no.nav.su.se.bakover.client.historisk.CountResponse
import no.nav.su.se.bakover.client.historisk.UttrekkResponse
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.domain.historisk.HistoriskImportOversikt
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonOversikt
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskMånedsbeløpForVedtak
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtaksperiode
import java.util.UUID

/**
 * Leser fra supstonad-historisk og kan kopiere de avtalte tabellene til et lokalt, tapsfritt rådatasnapshot.
 * Rådataene projiseres ikke til dagens vedtaksmodell her.
 */
interface SupstonadHistoriskService {
    fun tellRader(tabellnavn: String): Either<ClientError, CountResponse>

    fun hentAlleImporter(): List<HistoriskImportOversikt>

    fun opprettAldersprojeksjon(
        importId: UUID,
        maksAntallStønader: Int? = null,
    ): Either<KunneIkkeKonvertereHistoriskeData, UUID>

    suspend fun konverterAldersstønader(
        projeksjonId: UUID,
        importId: UUID,
        maksAntallStønader: Int? = null,
    ): Either<KunneIkkeKonvertereHistoriskeData, HistoriskAlderProjeksjonsresultat>

    fun hentAldersprojeksjoner(importId: UUID): List<HistoriskAlderProjeksjonOversikt>

    fun slettAldersprojeksjon(
        importId: UUID,
        projeksjonId: UUID,
    ): Either<KunneIkkeSletteHistoriskAlderProjeksjon, Unit>

    fun harHistoriskAlderssak(personident: String): Boolean

    fun hentHistoriskeAldersvedtaksperioder(personident: String): List<HistoriskVedtaksperiode>

    fun hentHistoriskeAldersmånedsbeløp(vedtakId: HistoriskVedtakId): HistoriskMånedsbeløpForVedtak?

    fun slettImport(importId: UUID): Either<KunneIkkeSletteImport, Unit>

    fun hentUttrekk(
        tabellnavn: String,
        antallRader: Long,
        iterator: String? = null,
    ): Either<ClientError, UttrekkResponse>

    fun importerAlleTabeller(
        sideStørrelse: Long = STANDARD_ANTALL_RADER_PER_SIDE,
        midlertidigUtenValidering: Boolean = false,
    ): Either<KunneIkkeImportereHistoriskeData, HistoriskImportresultat>

    fun ryddOppPågåendeImportVedOppstart()

    companion object {
        const val STANDARD_ANTALL_RADER_PER_SIDE = 20_000L
        const val MAKS_ANTALL_RADER_PER_SIDE = 100_000L
    }
}
