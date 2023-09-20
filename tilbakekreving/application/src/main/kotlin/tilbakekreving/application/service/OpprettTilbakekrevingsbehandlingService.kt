package tilbakekreving.application.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.opprett.KunneIkkeOppretteTilbakekrevingsbehandling
import tilbakekreving.domain.opprett.OpprettTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.opprett.OpprettetTilbakekrevingsbehandling
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.opprett.opprettTilbakekrevingsbehandling
import java.time.Clock

class OpprettTilbakekrevingsbehandlingService(
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val hendelseRepo: HendelseRepo,
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun opprett(
        command: OpprettTilbakekrevingsbehandlingCommand,
        kravgrunnlagMapper: (RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
    ): Either<KunneIkkeOppretteTilbakekrevingsbehandling, OpprettetTilbakekrevingsbehandling> {
        val sakId = command.sakId
        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            return KunneIkkeOppretteTilbakekrevingsbehandling.IkkeTilgang(it).left()
        }

        // Det er ikke sikkert vi har en hendelse på saken, i så fall genererer vi en ny.
        val sisteVersjon = hendelseRepo.hentSisteVersjonFraEntitetId(sakId) ?: Hendelsesversjon.ny()

        // TODO - en sjekk på at kravgrunnlaget ikke har en aktiv behandling (bør gå via Sak.kt)
        //  Da får vi en naturlig inngang og kravgrunnlaget bør ligge på saken slik at vi slipper mappingsbiten her.
        return kravgrunnlagRepo.hentÅpentKravgrunnlagForSak(sakId)?.let {
            kravgrunnlagMapper(it).map { kravgrunnlag ->
                opprettTilbakekrevingsbehandling(
                    command = command,
                    forrigeVersjon = sisteVersjon,
                    clock = clock,
                    kravgrunnlag = kravgrunnlag,
                )
            }.getOrElse {
                throw IllegalStateException("Feil ved mapping av kravgrunnlag på sak $sakId", it)
            }.let {
                tilbakekrevingsbehandlingRepo.opprett(it.first)
                it.second.right()
            }
        } ?: KunneIkkeOppretteTilbakekrevingsbehandling.IngenÅpneKravgrunnlag.left()
    }
}
