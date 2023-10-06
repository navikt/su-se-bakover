package tilbakekreving.application.service.common

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.person.PersonService
import tilbakekreving.domain.IkkeTilgangTilSak
import java.util.UUID

/**
 * Tanken her er at vi gradvis erstatter AccessCheckProxy med tilgangstyring per modul, for å desentralisere det.
 * Det blir noe duplisering i en periode.
 *
 * TODO: trekk ut PersonService/PersonRepo til egen modul
 */
class TilbakekrevingsbehandlingTilgangstyringService(
    private val personRepo: PersonRepo,
    private val personService: PersonService,
) {
    fun assertHarTilgangTilSak(sakId: UUID): Either<IkkeTilgangTilSak, Unit> {
        // TODO jah: Flytt denne inn i servicen, vi skal ikke trenge bruke person sitt repo direkte.
        return personRepo.hentFnrForSak(sakId).map {
            assertHarTilgangTilPerson(it)
        }.firstOrNull { it.isLeft() } ?: Unit.right()
    }

    private fun assertHarTilgangTilPerson(fnr: Fnr): Either<IkkeTilgangTilSak, Unit> {
        return personService.sjekkTilgangTilPerson(fnr).mapLeft { IkkeTilgangTilSak(it) }
    }
}
