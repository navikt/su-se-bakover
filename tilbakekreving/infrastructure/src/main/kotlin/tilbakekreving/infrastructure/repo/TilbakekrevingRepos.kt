package tilbakekreving.infrastructure.repo

import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.kravgrunnlag.KravgrunnlagPostgresRepo
import java.time.Clock

/**
 * Et forsøk på modularisering av [no.nav.su.se.bakover.domain.DatabaseRepos] der de forskjellige modulene er ansvarlige for å wire opp sine komponenter.
 *
 */
class TilbakekrevingRepos(
    val kravgrunnlagRepo: KravgrunnlagRepo,
    val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
) {
    companion object {
        fun create(
            clock: Clock,
            sessionFactory: SessionFactory,
            hendelseRepo: HendelseRepo,
            hendelsekonsumenterRepo: HendelsekonsumenterRepo,
            oppgaveHendelseRepo: OppgaveHendelseRepo,
            dokumentHendelseRepo: DokumentHendelseRepo,
        ): TilbakekrevingRepos {
            val kravgrunnlagRepo = KravgrunnlagPostgresRepo(
                hendelseRepo = hendelseRepo,
                hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            )
            return TilbakekrevingRepos(
                kravgrunnlagRepo = kravgrunnlagRepo,
                tilbakekrevingsbehandlingRepo = TilbakekrevingsbehandlingPostgresRepo(
                    sessionFactory = sessionFactory,
                    hendelseRepo = hendelseRepo,
                    clock = clock,
                    kravgrunnlagRepo = kravgrunnlagRepo,
                    oppgaveRepo = oppgaveHendelseRepo,
                    dokumentHendelseRepo = dokumentHendelseRepo,
                ),
            )
        }
    }
}
