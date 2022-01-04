package no.nav.su.se.bakover.domain.klage

/**
 * Se https://github.com/navikt/kabal-api/blob/main/docs/schema/klagevedtak-fattet.json
 * og https://github.com/navikt/kabal-api/blob/main/src/main/kotlin/no/nav/klage/oppgave/domain/kafka/KlagevedtakFattet.kt
 * og https://github.com/navikt/kabal-api/blob/a111f763916a1061db873867ce26ec0c7dd1d186/src/main/kotlin/no/nav/klage/oppgave/domain/kafka/ExternalUtfall.kt
 */
data class FattetKlagevedtak(
    /** Unik id for eventen som sendte vedtaket fra Kabal. Kabal lagrer denne internt som UUID. Og nevner at den kan brukes til idempotency av konsumentene. */
    val eventId: String,
    /** Ekstern id for klage. Skal stemme overens med id sendt inn. */
    val kildeReferanse: String,
    /** Kilden som sendte inn klage. Skal stemme overens med kilde sendt inn. */
    val kilde: String,
    /**
     * Utfallet av vedtaket. Kabal lagrer denne internt som en enum class. Mulige verdier:
     * - TRUKKET
     * - RETUR
     * - OPPHEVET
     * - MEDHOLD
     * - DELVIS_MEDHOLD
     * - STADFESTELSE
     * - UGUNST
     * - AVVIST
     */
    val utfall: String,
    /** Journalpost id til vedtaksbrev. */
    val vedtaksbrevReferanse: String?,
    /** Intern referanse fra kabal. Er per i dag vedtak_id i deres database. Kan i fremtiden brukes for å hente data om vedtak fra Kabal (se Swagger doc) */
    val kabalReferanse: String,
)
