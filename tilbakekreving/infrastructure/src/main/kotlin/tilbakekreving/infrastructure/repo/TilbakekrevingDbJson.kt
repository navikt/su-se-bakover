package tilbakekreving.infrastructure.repo

import java.util.UUID

/**
 * For enklere å kunne gjøre aggregerte spørringer mot tilbakekrevingshendelser i databasen.
 */
interface TilbakekrevingDbJson {
    val behandlingsId: UUID
    val utførtAv: String
}
