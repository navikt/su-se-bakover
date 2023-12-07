package no.nav.su.se.bakover.oppgave.infrastructure

import no.nav.su.se.bakover.common.infrastructure.ident.BrukerrolleJson
import no.nav.su.se.bakover.common.infrastructure.ident.toBrukerrollerJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata

/**
 * Se også no.nav.su.se.bakover.hendelse.infrastructure.persistence.DefaultMetadataJson
 */
private data class OppgaveHendelseMetadataDbJson(
    val correlationId: String?,
    val ident: String?,
    val brukerroller: List<BrukerrolleJson>,
    val jsonRequest: String?,
    val jsonResponse: String?,
)

fun OppgaveHendelseMetadata.toDbJson(): String {
    return OppgaveHendelseMetadataDbJson(
        correlationId = this.correlationId?.toString(),
        ident = this.ident?.toString(),
        brukerroller = this.brukerroller.toBrukerrollerJson(),
        jsonRequest = this.request,
        jsonResponse = this.response,
    ).let {
        serialize(it)
    }
}
