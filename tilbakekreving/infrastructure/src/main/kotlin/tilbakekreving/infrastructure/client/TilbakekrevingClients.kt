package tilbakekreving.infrastructure.client

import no.nav.su.se.bakover.common.domain.auth.SamlTokenProvider
import tilbakekreving.domain.vedtak.Tilbakekrevingsklient
import java.time.Clock

class TilbakekrevingClients(
    val tilbakekrevingsklient: Tilbakekrevingsklient,
) {
    companion object {
        fun create(
            baseUrl: String,
            samlTokenProvider: SamlTokenProvider,
            clock: Clock,
        ): TilbakekrevingClients {
            return TilbakekrevingClients(
                tilbakekrevingsklient = TilbakekrevingSoapClient(
                    baseUrl = baseUrl,
                    samlTokenProvider = samlTokenProvider,
                    clock = clock,
                ),
            )
        }
    }
}
