package no.nav.su.se.bakover.web.routes.vilkår

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test

class PersonligOppholdVilkårJsonTest {
    @Test
    fun `tom vurderingsliste returnerer bad request`() {
        emptyList<LeggTilVurderingsperiodePersonligOppmøteJson>().toDomain(fixedClock) shouldBe
            HttpStatusCode.BadRequest.errorJson(
                "Må oppgi minst én vurderingsperiode for personlig oppmøte",
                "mangler_vurderingsperioder_personlig_oppmøte",
            ).left()
    }
}
