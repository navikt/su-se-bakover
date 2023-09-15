package no.nav.su.se.bakover.tilbakekreving.application.service

import arrow.core.Either
import no.nav.su.se.bakover.tilbakekreving.domain.Tilbakekrevingsbehandling
import java.util.UUID

class TilbakekrevingService {

    fun ny(sakId: UUID): Either<KunneIkkeOppretteTilbakekrevingsbehandling, Tilbakekrevingsbehandling> {
        println(sakId)
        TODO()
    }
}
