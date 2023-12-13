package no.nav.su.se.bakover.dokument.infrastructure

import dokument.domain.Dokument
import java.util.UUID

/**
 * Per nå støtter kun hendelser for tilbakekreving
 */
data class DokumentMetaDataDbJson(
    val sakId: UUID,
    val tilbakekrevingsbehandlingId: UUID?,
    val vedtakId: UUID?,
    val journalpostId: String?,
    val brevbestillingsId: String?,
) {
    companion object {
        fun Dokument.Metadata.toHendelseDbJson(): DokumentMetaDataDbJson = DokumentMetaDataDbJson(
            sakId = this.sakId,
            tilbakekrevingsbehandlingId = this.tilbakekrevingsbehandlingId,
            vedtakId = this.vedtakId,
            journalpostId = this.journalpostId,
            brevbestillingsId = this.brevbestillingId,
        )
    }
}
