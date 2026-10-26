package no.nav.su.se.bakover.web.routes.vilkår

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.LeggTilVurderingsperiodeFlyktningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.toDomain
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.VurderingsperiodeOpplysningspliktVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.toDomain
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.LeggTilVurderingsperiodePensjonsvilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.toDomain
import no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold.UtenlandsoppholdBody
import org.junit.jupiter.api.Test
import java.util.UUID

class TommeVurderingslisterTest {
    @Test
    fun `flyktningvilkår avviser tom liste med bad request`() {
        emptyList<LeggTilVurderingsperiodeFlyktningVilkårJson>().toDomain(fixedClock)
            .mapLeft { it.httpCode } shouldBe HttpStatusCode.BadRequest.left()
    }

    @Test
    fun `pensjonsvilkår avviser tom liste med bad request`() {
        emptyList<LeggTilVurderingsperiodePensjonsvilkårJson>().toDomain(fixedClock)
            .mapLeft { it.httpCode } shouldBe HttpStatusCode.BadRequest.left()
    }

    @Test
    fun `opplysningsplikt avviser tom liste med bad request`() {
        emptyList<VurderingsperiodeOpplysningspliktVilkårJson>().toDomain(fixedClock)
            .mapLeft { it.httpCode } shouldBe HttpStatusCode.BadRequest.left()
    }

    @Test
    fun `utenlandsopphold avviser tom liste med bad request`() {
        UtenlandsoppholdBody(emptyList()).toDomain(SøknadsbehandlingId(UUID.randomUUID()))
            .mapLeft { it.httpCode } shouldBe HttpStatusCode.BadRequest.left()
    }
}
