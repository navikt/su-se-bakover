package no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon

import arrow.core.Either
import arrow.core.raise.either
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import java.time.Clock
import java.util.UUID

data class LeggTilBosituasjonerRequest(
    val behandlingId: UUID,
    val bosituasjoner: List<LeggTilBosituasjonRequest>,
) {
    fun toDomain(
        clock: Clock,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, List<Grunnlag.Bosituasjon.Fullstendig>> {
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
