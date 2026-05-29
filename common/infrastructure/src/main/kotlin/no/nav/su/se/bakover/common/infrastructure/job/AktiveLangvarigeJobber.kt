package no.nav.su.se.bakover.common.infrastructure.job

import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Register over langvarige jobber som er sårbare for pod-restart (deploy, cluster autoscaler,
 * node-oppgradering, eviction osv.). Brukes av shutdown-hooken i web-modulen for å rapportere
 * hvilke jobber som blir avbrutt midt i når pod-en stenges ned.
 *
 * Bruksmønster:
 * ```
 * AktiveLangvarigeJobber.kjør(navn = "automatisk-regulering", metadata = mapOf(...)) { kjøringId ->
 *     // ... langvarig arbeid ...
 * }
 * ```
 *
 * Jobben blir registrert ved start og fjernet i finally-blokken — uavhengig av om [kjør] kaster
 * eller returnerer normalt.
 */
object AktiveLangvarigeJobber {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobber = ConcurrentHashMap<UUID, Jobb>()

    data class Jobb(
        val id: UUID,
        val navn: String,
        val startet: Instant,
        val metadata: Map<String, String>,
    )

    /**
     * Registrerer en langvarig jobb for hele livsløpet av [block]. Den genererte [UUID]-en sendes
     * inn i [block] slik at kallende kode kan bruke samme id til logging, persistens og lignende.
     */
    fun <T> kjør(
        navn: String,
        metadata: Map<String, String> = emptyMap(),
        block: (UUID) -> T,
    ): T {
        val jobb = Jobb(
            id = UUID.randomUUID(),
            navn = navn,
            startet = Instant.now(),
            metadata = metadata,
        )
        jobber[jobb.id] = jobb
        log.info("Starter langvarig jobb '$navn' (id=${jobb.id}, metadata=$metadata)")
        return try {
            block(jobb.id)
        } finally {
            jobber.remove(jobb.id)
            log.info("Avsluttet langvarig jobb '$navn' (id=${jobb.id})")
        }
    }

    fun aktive(): List<Jobb> = jobber.values.toList()
}
