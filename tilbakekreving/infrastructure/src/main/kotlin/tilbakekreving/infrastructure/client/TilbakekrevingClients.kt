package tilbakekreving.infrastructure.client

import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import tilbakekreving.domain.vedtak.Tilbakekrevingsklient
import java.time.Clock

class TilbakekrevingClients(
    val tilbakekrevingsklient: Tilbakekrevingsklient,
) {
    companion object {
        fun create(
            clock: Clock,
            suProxyConfig: SuProxyConfig,
            azureAd: AzureAd,
        ): TilbakekrevingClients {
            val tilbakekrevingsklient = TilbakekrevingProxyClient(
                config = suProxyConfig,
                azureAd = azureAd,
                clock = clock,
            )
            return TilbakekrevingClients(tilbakekrevingsklient = tilbakekrevingsklient)
        }
    }
}
