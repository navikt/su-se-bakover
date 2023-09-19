package no.nav.su.se.bakover.tilbakekreving.application.service

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.tilbakekreving.domain.Kravgrunnlag
import no.nav.su.se.bakover.tilbakekreving.domain.KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag
import no.nav.su.se.bakover.tilbakekreving.domain.KunneIkkeOppretteTilbakekrevingsbehandling
import no.nav.su.se.bakover.tilbakekreving.domain.ManuellTilbakekrevingService
import no.nav.su.se.bakover.tilbakekreving.domain.ManuellTilbakekrevingsbehandling
import no.nav.su.se.bakover.tilbakekreving.domain.RåttKravgrunnlag
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class ManuellTilbakekrevingServiceImpl(
    private val tilbakekrevingRepo: TilbakekrevingRepo,
    private val clock: Clock,
) : ManuellTilbakekrevingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentAktivKravgrunnlag(
        sakId: UUID,
        kravgrunnlagMapper: (RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
    ): Either<KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag, Kravgrunnlag> {
        return tilbakekrevingRepo.hentSisteFerdigbehandledeKravgrunnlagForSak(sakId)?.let {
            kravgrunnlagMapper(it).map {
                it
            }.mapLeft {
                log.error("Feil ved mapping av kravgrunnlag på sak $sakId")
                KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.FeilVedMappingAvKravgrunnalget
            }
        } ?: KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.FinnesIngenFerdigBehandledeKravgrunnlag.left()
    }

    override fun ny(
        sakId: UUID,
        kravgrunnlagMapper: (RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
    ): Either<KunneIkkeOppretteTilbakekrevingsbehandling, ManuellTilbakekrevingsbehandling> {
        // TODO - en sjekk på at kravgrunnlaget ikke har en aktiv behandling
        return tilbakekrevingRepo.hentSisteFerdigbehandledeKravgrunnlagForSak(sakId)?.let {
            kravgrunnlagMapper(it).map {
                ManuellTilbakekrevingsbehandling(
                    id = UUID.randomUUID(),
                    sakId = sakId,
                    opprettet = Tidspunkt.now(clock),
                    kravgrunnlag = it,
                )
                // TODO - Her vil vi ha en form for lagring
            }.mapLeft {
                log.error("Feil ved mapping av kravgrunnlag på sak $sakId")
                KunneIkkeOppretteTilbakekrevingsbehandling.FeilVedMappingAvKravgrunnalget
            }
        } ?: KunneIkkeOppretteTilbakekrevingsbehandling.FinnesIngenFerdigBehandledeKravgrunnlag.left()
    }
}
