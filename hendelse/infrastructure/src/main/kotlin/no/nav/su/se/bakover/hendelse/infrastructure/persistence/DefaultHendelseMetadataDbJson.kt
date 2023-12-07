package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.infrastructure.ident.BrukerrolleJson
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson.Companion.toIdentJson
import no.nav.su.se.bakover.common.infrastructure.ident.toBrukerrollerJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata

private data class DefaultHendelseMetadataDbJson(
    val correlationId: String?,
    val ident: IdentJson?,
    val roller: List<BrukerrolleJson>,
)

fun DefaultHendelseMetadata.toDbJson(): String {
    return serialize(
        DefaultHendelseMetadataDbJson(
            correlationId = this.correlationId?.toString(),
            ident = this.ident?.toIdentJson(),
            roller = this.brukerroller.toBrukerrollerJson(),
        ),
    )
}
