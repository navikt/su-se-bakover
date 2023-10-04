package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.ident.BrukerrolleJson
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson.Companion.toIdentJson
import no.nav.su.se.bakover.common.infrastructure.ident.toBrukerroller
import no.nav.su.se.bakover.common.infrastructure.ident.toBrukerrollerJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelse

internal data class DefaultMetadataJson(
    val correlationId: String?,
    val ident: IdentJson?,
    val roller: List<BrukerrolleJson>,
) {
    companion object {
        fun Hendelse<*>.toMeta(): String {
            return serialize(
                DefaultMetadataJson(
                    correlationId = this.meta.correlationId?.toString(),
                    ident = this.meta.ident?.toIdentJson(),
                    roller = this.meta.brukerroller.toBrukerrollerJson(),
                ),
            )
        }

        fun toDomain(json: String) = deserialize<DefaultMetadataJson>(json).let {
            DefaultHendelseMetadata(
                correlationId = it.correlationId?.let { CorrelationId(it) },
                ident = it.ident?.toDomain(),
                brukerroller = it.roller.toBrukerroller(),
            )
        }
    }
}
