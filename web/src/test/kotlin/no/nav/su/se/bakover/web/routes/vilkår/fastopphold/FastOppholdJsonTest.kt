package no.nav.su.se.bakover.web.routes.vilkår.fastopphold

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.vilkår.fastopphold.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test

class FastOppholdJsonTest {
    @Test
    fun `tom vurderingsliste gir valideringsfeil og bad request`() {
        val forventetFeil = KunneIkkeLeggeFastOppholdINorgeVilkår.ManglerVurderingsperioder
        val resultat = emptyList<LeggTilVurderingsperiodeFastOppholdJson>().toDomain(fixedClock)

        resultat shouldBe forventetFeil.tilResultat().left()
        resultat.mapLeft { it.httpCode } shouldBe HttpStatusCode.BadRequest.left()
    }
}
