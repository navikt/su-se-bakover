package tilbakekreving.presentation.api

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.person.PersonService
import tilbakekreving.application.service.HentÅpentKravgrunnlagService
import tilbakekreving.application.service.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.application.service.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.infrastructure.KravgrunnlagPostgresRepo
import tilbakekreving.presentation.api.hent.hentKravgrunnlagRoute
import tilbakekreving.presentation.api.opprett.opprettTilbakekrevingsbehandlingRoute
import java.time.Clock

internal const val tilbakekrevingPath = "saker/{sakId}/tilbakekreving"

fun Route.tilbakekrevingRoutes(
    personRepo: PersonRepo,
    personService: PersonService,
    sessionFactory: PostgresSessionFactory,
    clock: Clock,
) {
    val tilgangstyringService = TilbakekrevingsbehandlingTilgangstyringService(
        personRepo = personRepo,
        personService = personService,
    )
    val kravgrunnlagRepo = KravgrunnlagPostgresRepo(
        sessionFactory = sessionFactory,
    )
    this.hentKravgrunnlagRoute(
        HentÅpentKravgrunnlagService(
            kravgrunnlagRepo = kravgrunnlagRepo,
            tilgangstyring = tilgangstyringService,
        ),
    )
    this.opprettTilbakekrevingsbehandlingRoute(
        OpprettTilbakekrevingsbehandlingService(
            kravgrunnlagRepo = kravgrunnlagRepo,
            tilgangstyring = tilgangstyringService,
            clock = clock,
        ),
    )
}
