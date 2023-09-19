package tilbakekreving.application.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.Tidspunkt
import org.slf4j.LoggerFactory
import tilbakekreving.domain.ManuellTilbakekrevingsbehandling
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.opprett.KunneIkkeOppretteTilbakekrevingsbehandling
import java.time.Clock
import java.util.UUID

class OpprettTilbakekrevingsbehandlingService(
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun opprett(
        sakId: UUID,
        kravgrunnlagMapper: (RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
    ): Either<KunneIkkeOppretteTilbakekrevingsbehandling, ManuellTilbakekrevingsbehandling> {
        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            return KunneIkkeOppretteTilbakekrevingsbehandling.IkkeTilgang(it).left()
        }

        // TODO - en sjekk på at kravgrunnlaget ikke har en aktiv behandling
        return kravgrunnlagRepo.hentÅpentKravgrunnlagForSak(sakId)?.let {
            kravgrunnlagMapper(it).map {
                ManuellTilbakekrevingsbehandling(
                    id = UUID.randomUUID(),
                    sakId = sakId,
                    opprettet = Tidspunkt.now(clock),
                    kravgrunnlag = it,
                )
                // TODO - Her vil vi ha en form for lagring
            }.getOrElse {
                throw IllegalStateException("Feil ved mapping av kravgrunnlag på sak $sakId", it)
            }.right()
        } ?: KunneIkkeOppretteTilbakekrevingsbehandling.IngenÅpneKravgrunnlag.left()
    }
}
