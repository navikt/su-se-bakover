package no.nav.su.se.bakover.client.oppdrag.tilbakekreving

import arrow.core.Either
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingPortType
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.KunneIkkeSendeKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak
import org.slf4j.LoggerFactory

class TilbakekrevingSoapClient(
    private val tilbakekrevingPortType: TilbakekrevingPortType,
) : TilbakekrevingClient {

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Sender informasjon til oppdrag hvordan vi vil avgjøre om vi vil kreve tilbake eller ikke.
     * TODO jah: Returner en slags respons istedtfor Unit?
     */
    override fun avgjørKravgrunnlag(avgjørelse: Tilbakekrevingsvedtak): Either<KunneIkkeSendeKravgrunnlag, Unit> {
        return Either.catch {
            tilbakekrevingPortType.tilbakekrevingsvedtak(mapToTilbakekrevingsvedtakRequest(avgjørelse))
            Unit
        }.mapLeft {
            log.error("SOAP kall mot oppdrag (tilbakekreving) feilet", it)
            // Kan tolke feilmeldingene etterhvert som de dukker opp.
            KunneIkkeSendeKravgrunnlag
        }
    }
}
