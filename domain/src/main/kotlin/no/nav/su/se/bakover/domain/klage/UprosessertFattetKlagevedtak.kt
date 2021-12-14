package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.Tidspunkt
import java.util.UUID

/**
 * Representerer foreløpig kun et uprosessert fattet klagevedtak (Kabal).
 * Vi har filtrert ut hendelser som ikke angår Supplerende Stønad Uføre.
 */
data class UprosessertFattetKlagevedtak(
    val id: UUID,
    val opprettet: Tidspunkt,
    val metadata: Metadata,
) {
    /** Metadata rundt hendelsen
     * Et offset er kun unikt kombinert med partisjonen (direkte tilknyttet Kafka)
     * */
    data class Metadata(
        val hendelseId: String,
        val offset: Long,
        val partisjon: Int,
        /** Kafkameldinger kommer som key-value pairs ('key' inneholder Kabal sin interne UUID) */
        val key: String,
        /** Kafkameldinger kommer som key-value pairs ('value' er den rå json-meldingen) */
        val value: String,
    )
}
