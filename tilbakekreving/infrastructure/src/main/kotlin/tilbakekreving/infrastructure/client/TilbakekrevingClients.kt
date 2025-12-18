package tilbakekreving.infrastructure.client

import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.auth.SamlTokenProvider
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import no.nav.su.se.bakover.common.infrastructure.config.isGCP
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
            suProxyConfig: SuProxyConfig,
            azureAd: AzureAd,
        ): TilbakekrevingClients {
            val tilbakekrevingsklient = if (isGCP()) {
                TilbakekrevingProxyClient(
                    config = suProxyConfig,
                    azureAd = azureAd,
                    clock = clock,
                )
            } else {
                TilbakekrevingSoapClient(
                    baseUrl = baseUrl,
                    samlTokenProvider = samlTokenProvider,
                    clock = clock,
                )
            }
            return TilbakekrevingClients(tilbakekrevingsklient = tilbakekrevingsklient)
        }
    }
}
