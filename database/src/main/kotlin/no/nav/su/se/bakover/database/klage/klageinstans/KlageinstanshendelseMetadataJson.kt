package no.nav.su.se.bakover.database.klage.klageinstans

import behandling.klage.domain.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize

/**
 * Brukes for å serialisere/deserialisere metadata-feltet i klageinstanshendelse-tabellen
 */
internal data class KlageinstanshendelseMetadataJson(
    val topic: String,
    val hendelseId: String,
    val offset: Long,
    val partisjon: Int,
    val key: String,
    val value: String,
) {
    companion object {
        fun toKlageinstanshendelseMetadata(value: String): UprosessertKlageinstanshendelse.Metadata {
            return deserialize<KlageinstanshendelseMetadataJson>(value).let {
                UprosessertKlageinstanshendelse.Metadata(
                    topic = it.topic,
                    hendelseId = it.hendelseId,
                    offset = it.offset,
                    partisjon = it.partisjon,
                    key = it.key,
                    value = it.value,
                )
            }
        }
    }
}

internal fun UprosessertKlageinstanshendelse.Metadata.toDatabaseJson(): String {
    return KlageinstanshendelseMetadataJson(
        topic = topic,
        hendelseId = hendelseId,
        offset = offset,
        partisjon = partisjon,
        key = key,
        value = value,
    ).let { serialize(it) }
}
