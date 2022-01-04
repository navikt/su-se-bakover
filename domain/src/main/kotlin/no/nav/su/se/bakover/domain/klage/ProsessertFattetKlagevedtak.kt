package no.nav.su.se.bakover.domain.klage

/**
 * Representerer ett fattet klagevedtak av Kabal som har blitt prosessert av oss.
 */
sealed class ProsessertFattetKlagevedtak {
    abstract val eventId: String
    abstract val utfall: String
    abstract val kildeReferanse: String

    data class Ferdig(
        override val eventId: String,
        override val utfall: String,
        override val kildeReferanse: String,
    ) : ProsessertFattetKlagevedtak()

    data class Feilet(
        override val eventId: String,
        override val utfall: String,
        override val kildeReferanse: String,
    ) : ProsessertFattetKlagevedtak()
}

