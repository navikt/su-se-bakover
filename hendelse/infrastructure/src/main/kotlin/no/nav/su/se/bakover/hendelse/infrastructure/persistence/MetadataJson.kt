package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata

internal data class MetadataJson(
    val correlationId: String,
    val ident: IdentJson,
) {
    companion object {
        internal fun Hendelse.toMeta(): String {
            return serialize(
                HendelseMetadata(
                    correlationId = this.meta.correlationId,
                    ident = this.meta.ident,
                ),
            )
        }
    }

    fun toDomain() = HendelseMetadata(
        correlationId = CorrelationId(this.correlationId),
        ident = this.ident.toDomain(),
    )
}
