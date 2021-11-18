package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage

interface KlageService {
    fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage>
    fun vilkårsvurder(request: VurderKlagevilkårRequest): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage>
}

sealed class KunneIkkeOppretteKlage {
    object FantIkkeSak : KunneIkkeOppretteKlage()
    object FinnesAlleredeEnÅpenKlage : KunneIkkeOppretteKlage()
}
