package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import java.util.UUID

interface KlageService {
    fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage>
    fun vilkårsvurder(request: VurderKlagevilkårRequest): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage>
    fun brevutkast(
        sakId: UUID,
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
        hjemler: Hjemler.Utfylt
    ): Either<KunneIkkeLageBrevutkast, ByteArray>
    fun vurder(request: KlageVurderingerRequest): Either<KunneIkkeVurdereKlage, VurdertKlage>
}

sealed class KunneIkkeOppretteKlage {
    object FantIkkeSak : KunneIkkeOppretteKlage()
    object FinnesAlleredeEnÅpenKlage : KunneIkkeOppretteKlage()
}

sealed class KunneIkkeLageBrevutkast {
    object FantIkkeSak : KunneIkkeLageBrevutkast()
    object FantIkkePerson : KunneIkkeLageBrevutkast()
    object FantIkkeSaksbehandler : KunneIkkeLageBrevutkast()
    object UgyldigKlagetyp : KunneIkkeLageBrevutkast()
    object GenereringAvBrevFeilet : KunneIkkeLageBrevutkast()
}
