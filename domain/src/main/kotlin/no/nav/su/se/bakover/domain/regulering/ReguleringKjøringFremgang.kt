package no.nav.su.se.bakover.domain.regulering

import java.time.Instant
import java.util.UUID

/**
 * Delvis fremgang for én batch innenfor en [ReguleringKjøring]. Lagres etter at hver batch
 * er ferdigbehandlet, slik at vi har resultater også når jobben blir avbrutt midt i
 * (pod-restart, deploy, cluster-autoscaler etc.). Mister da i verste fall den batchen som
 * var midt i — alle tidligere batcher er trygt persistert.
 *
 * For å rekonstruere full status for en kjøring: hent alle rader med samme [kjøringId]
 * sortert på [batchNummer] og slå sammen [resultater].
 *
 * Eksempler på oppslag — se også [ReguleringKjøringFremgangRepo]:
 * ```
 * // Hent siste statusen for en kjøring
 * val fremganger = repo.hentForKjøring(kjøringId)
 * val totaltBehandlet = fremganger.sumOf { it.sakerIBatch }
 * val alleResultater = fremganger.flatMap { it.resultater }
 * val sisteOppdatering = fremganger.maxOfOrNull { it.tidspunkt }
 * ```
 */
data class ReguleringKjøringFremgang(
    val kjøringId: UUID,
    val batchNummer: Int,
    val tidspunkt: Instant,
    val sakerIBatch: Int,
    val resultater: List<Reguleringsresultat>,
)

interface ReguleringKjøringFremgangRepo {
    /**
     * Lagrer fremgangen for én batch. Idempotent på (kjøringId, batchNummer) — gjentatt kall
     * med samme nøkkel gjør ingenting (databasenivå unique constraint + on conflict).
     */
    fun lagre(fremgang: ReguleringKjøringFremgang)

    /**
     * Henter alle batch-rader for én kjøring, sortert på [ReguleringKjøringFremgang.batchNummer].
     * Returnerer tom liste hvis kjøringen ikke finnes eller ikke har lagret noen batcher ennå.
     */
    fun hentForKjøring(kjøringId: UUID): List<ReguleringKjøringFremgang>
}
