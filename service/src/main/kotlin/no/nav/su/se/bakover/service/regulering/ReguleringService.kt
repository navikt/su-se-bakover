package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

data class SakSomKanReguleres(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val reguleringType: ReguleringType,
)

data class SakerSomKanReguleres(
    val saker: List<SakSomKanReguleres>,
)

data class BeregnRequest(
    val behandlingId: UUID,
    val begrunnelse: String?,
)

data class SimulerRequest(
    val behandlingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
)

sealed class KunneIkkeBeregne {
    object FantIkkeRegulering : KunneIkkeBeregne()
    data class UgyldigTilstand(
        val status: KClass<out Regulering>,
    ) : KunneIkkeBeregne()
}

sealed class KunneIkkeSimulere {
    object FantIkkeRegulering : KunneIkkeSimulere()
    object FantIkkeBeregning : KunneIkkeSimulere()
    object SimuleringFeilet : KunneIkkeSimulere()
    data class UgyldigTilstand(
        val status: KClass<out Regulering>,
    ) : KunneIkkeSimulere()
}

interface ReguleringService {
    fun hentAlleSakerSomKanReguleres(fraDato: LocalDate?): SakerSomKanReguleres
    fun kjørAutomatiskRegulering(fraDato: LocalDate?): List<Regulering>
    fun opprettRegulering(sakId: UUID, fraDato: LocalDate?): Either<KunneIkkeOppretteRegulering, Regulering>
    fun leggTilFradrag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeOppretteRegulering, Regulering>
    fun iverksett(reguleringId: UUID): Either<KunneIkkeOppretteRegulering, Regulering>
    fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, Regulering.OpprettetRegulering>
    fun simuler(request: SimulerRequest): Either<KunneIkkeSimulere, Regulering.OpprettetRegulering>
}
