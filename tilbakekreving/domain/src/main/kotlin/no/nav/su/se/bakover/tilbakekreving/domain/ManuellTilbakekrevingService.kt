package no.nav.su.se.bakover.tilbakekreving.domain

import arrow.core.Either
import java.util.UUID

interface ManuellTilbakekrevingService {
    fun hentAktivKravgrunnlag(
        sakId: UUID,
        kravgrunnlagMapper: (RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
    ): Either<KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag, Kravgrunnlag>

    fun ny(
        sakId: UUID,
        kravgrunnlagMapper: (RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
    ): Either<KunneIkkeOppretteTilbakekrevingsbehandling, ManuellTilbakekrevingsbehandling>
}
