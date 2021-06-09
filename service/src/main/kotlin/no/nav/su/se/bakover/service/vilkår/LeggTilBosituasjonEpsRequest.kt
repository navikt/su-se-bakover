package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import java.time.Clock
import java.util.UUID

data class LeggTilBosituasjonEpsRequest(
    val behandlingId: UUID,
    val epsFnr: Fnr?,
) {
    fun toBosituasjon(
        periode: Periode,
        clock: Clock,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag, Grunnlag.Bosituasjon.Ufullstendig> {
        return if (epsFnr == null) {
            Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = periode,
            )
        } else {
            hentPerson(epsFnr).getOrElse {
                return SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl.left()
            }
            Grunnlag.Bosituasjon.Ufullstendig.HarEps(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = periode,
                fnr = epsFnr,
            )
        }.right()
    }
}
