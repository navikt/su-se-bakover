package tilbakekreving.infrastructure.repo

import dokument.domain.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.dokument.infrastructure.DokumentHendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelseFilPostgresRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.kravgrunnlag.KravgrunnlagPostgresRepo
import java.time.Clock

/**
 * Et forsøk på modularisering av [no.nav.su.se.bakover.domain.DatabaseRepos] der de forskjellige modulene er ansvarlige for å wire opp sine komponenter.
 *
 * Det kan hende vi må splitte denne i en data class + builder.
 */
class TilbakekrevingRepos(
    val clock: Clock,
    val sessionFactory: SessionFactory,
    val hendelseRepo: HendelseRepo,
    val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    val oppgaveHendelseRepo: OppgaveHendelseRepo,
    val dokumentHendelseRepo: DokumentHendelseRepo = DokumentHendelsePostgresRepo(
        hendelseRepo = hendelseRepo,
        hendelseFilRepo = HendelseFilPostgresRepo(sessionFactory),
    ),
    val kravgrunnlagRepo: KravgrunnlagRepo = KravgrunnlagPostgresRepo(
        hendelseRepo = hendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
    ),
    val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo = TilbakekrevingsbehandlingPostgresRepo(
        sessionFactory = sessionFactory,
        hendelseRepo = hendelseRepo,
        clock = clock,
        kravgrunnlagRepo = kravgrunnlagRepo,
        oppgaveRepo = oppgaveHendelseRepo,
        dokumentHendelseRepo = dokumentHendelseRepo,
    ),
)
