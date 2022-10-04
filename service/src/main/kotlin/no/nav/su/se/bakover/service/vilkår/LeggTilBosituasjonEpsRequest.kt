package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
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
        harTilgangTilPerson: (fnr: Fnr) -> Either<SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl, Boolean>,
    ): Either<SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag, Grunnlag.Bosituasjon.Ufullstendig> {
        return if (epsFnr == null) {
            Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = periode,
            )
        } else {
            harTilgangTilPerson(epsFnr).getOrHandle {
                return it.left()
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
