package tilbakekreving.application.service.tilgang

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.PersonService
import tilbakekreving.domain.IkkeTilgangTilSak
import java.util.UUID

/**
 * Tanken her er at vi gradvis erstatter [no.nav.su.se.bakover.web.services.AccessCheckProxy] med tilgangstyring per modul, for å desentralisere det.
 * Det blir noe duplisering i en periode.
 */
class TilbakekrevingsbehandlingTilgangstyringService(
    private val personService: PersonService,
) {
    fun assertHarTilgangTilSak(sakId: UUID): Either<IkkeTilgangTilSak, Unit> {
        return personService.hentFnrForSak(sakId).map {
            assertHarTilgangTilPerson(it)
        }.firstOrNull { it.isLeft() } ?: Unit.right()
    }

    private fun assertHarTilgangTilPerson(fnr: Fnr): Either<IkkeTilgangTilSak, Unit> {
        return personService.sjekkTilgangTilPerson(fnr).mapLeft { IkkeTilgangTilSak(it) }
    }
}
