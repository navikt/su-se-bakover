package no.nav.su.se.bakover.tilbakekreving.application.service

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.tilbakekreving.domain.Kravgrunnlag
import no.nav.su.se.bakover.tilbakekreving.domain.KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag
import no.nav.su.se.bakover.tilbakekreving.domain.KunneIkkeOppretteTilbakekrevingsbehandling
import no.nav.su.se.bakover.tilbakekreving.domain.ManuellTilbakekrevingService
import no.nav.su.se.bakover.tilbakekreving.domain.RåttKravgrunnlag
import no.nav.su.se.bakover.tilbakekreving.domain.Tilbakekrevingsbehandling
import org.slf4j.LoggerFactory
import java.util.UUID

class ManuellTilbakekrevingServiceImpl(
    private val tilbakekrevingRepo: TilbakekrevingRepo,
) : ManuellTilbakekrevingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentAktivKravgrunnlag(
        sakId: UUID,
        kravgrunnlagMapper: (RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
    ): Either<KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag, Kravgrunnlag> {
        return tilbakekrevingRepo.hentSisteFerdigbehandledeKravgrunnlagForSak(sakId)?.kravgrunnlag?.let {
            kravgrunnlagMapper(it).map {
                it
            }.mapLeft {
                log.error("Feil ved mapping av kravgrunnlag på sak $sakId")
                KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.FeilVedMappingAvKravgrunnalget
            }
        } ?: KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.FinnesIngenFerdigBehandledeKravgrunnlag.left()
    }

    override fun ny(sakId: UUID): Either<KunneIkkeOppretteTilbakekrevingsbehandling, Tilbakekrevingsbehandling> {
        println(sakId)
        TODO()
    }
}
