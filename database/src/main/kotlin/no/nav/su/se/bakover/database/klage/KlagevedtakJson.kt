package no.nav.su.se.bakover.database.klage

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlageinstansvedtak

internal data class KlagevedtakMetadataJson(
    val hendelseId: String,
    val offset: Long,
    val partisjon: Int,
    val key: String,
    val value: String,
) {
    companion object {
        @Suppress("unused")
        fun toKlagevedtakMetadata(value: String): UprosessertFattetKlageinstansvedtak.Metadata {
            return deserialize<KlagevedtakMetadataJson>(value).let {
                UprosessertFattetKlageinstansvedtak.Metadata(
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

internal fun UprosessertFattetKlageinstansvedtak.Metadata.toDatabaseJson(): String {
    return KlagevedtakMetadataJson(
        hendelseId = hendelseId,
        offset = offset,
        partisjon = partisjon,
        key = key,
        value = value,
    ).let { serialize(it) }
}
