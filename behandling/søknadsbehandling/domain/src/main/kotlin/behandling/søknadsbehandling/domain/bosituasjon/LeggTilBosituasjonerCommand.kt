package behandling.søknadsbehandling.domain.bosituasjon

import arrow.core.Either
import arrow.core.raise.either
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import java.time.Clock

data class LeggTilBosituasjonerCommand(
    val behandlingId: BehandlingsId,
    val bosituasjoner: List<LeggTilBosituasjonCommand>,
) {
    fun toDomain(
        clock: Clock,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, List<Bosituasjon.Fullstendig>> {
        return either {
            bosituasjoner.map {
                it.toDomain(
                    clock = clock,
                    hentPerson = hentPerson,
                ).bind()
            }
        }
    }
}
