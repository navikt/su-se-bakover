package tilbakekreving.infrastructure.client

import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig
import tilbakekreving.domain.iverksett.Tilbakekrevingsklient
import java.time.Clock

class TilbakekrevingClients(
    val tilbakekrevingsklient: Tilbakekrevingsklient,
) {
    companion object {
        fun create(
            tilbakekrevingConfig: TilbakekrevingConfig,
            clock: Clock,
        ): TilbakekrevingClients {
            return TilbakekrevingClients(
                tilbakekrevingsklient = TilbakekrevingSoapClient(
                    tilbakekrevingPortType = TilbakekrevingSoapClientConfig(
                        tilbakekrevingConfig = tilbakekrevingConfig,
                    ).tilbakekrevingSoapService(),
                    clock = clock,
                ),
            )
        }
    }
}
