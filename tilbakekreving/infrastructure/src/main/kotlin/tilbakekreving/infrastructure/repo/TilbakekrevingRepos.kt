package tilbakekreving.infrastructure.repo

import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.kravgrunnlag.repo.KravgrunnlagRepo
import tilbakekreving.infrastructure.repo.kravgrunnlag.KravgrunnlagPostgresRepo
import tilbakekreving.infrastructure.repo.sammendrag.BehandlingssammendragKravgrunnlagOgTilbakekrevingPostgresRepo
import java.time.Clock

/**
 * Et forsøk på modularisering av [no.nav.su.se.bakover.domain.DatabaseRepos] der de forskjellige modulene er ansvarlige for å wire opp sine komponenter.
 *
 */
class TilbakekrevingRepos(
    val kravgrunnlagRepo: KravgrunnlagRepo,
    val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    val kravgrunnlagOgIverksatteTilbakekrevingerPostgresRepo: BehandlingssammendragKravgrunnlagOgTilbakekrevingPostgresRepo,
) {
    companion object {
        fun create(
            clock: Clock,
            sessionFactory: SessionFactory,
            hendelseRepo: HendelseRepo,
            hendelsekonsumenterRepo: HendelsekonsumenterRepo,
            dokumentHendelseRepo: DokumentHendelseRepo,
            dbMetrics: DbMetrics,
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
                    dokumentHendelseRepo = dokumentHendelseRepo,
                ),
                kravgrunnlagOgIverksatteTilbakekrevingerPostgresRepo = BehandlingssammendragKravgrunnlagOgTilbakekrevingPostgresRepo(
                    dbMetrics = dbMetrics,
                    sessionFactory = sessionFactory,
                ),
            )
        }
    }
}
