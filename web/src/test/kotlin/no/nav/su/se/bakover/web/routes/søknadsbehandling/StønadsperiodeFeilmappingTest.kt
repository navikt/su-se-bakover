package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.KunneIkkeOppdatereStønadsperiode
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.vurderinger.domain.Konsistensproblem
import vilkår.vurderinger.domain.KunneIkkeLageGrunnlagsdata

class StønadsperiodeFeilmappingTest {
    @Test
    fun `grunnlagsfeil pakkes ut og gir konkret valideringsfeil`() {
        listOf(
            KunneIkkeLageGrunnlagsdata.MåLeggeTilBosituasjonFørFradrag to "må_ha_bosituasjon_før_fradrag",
            KunneIkkeLageGrunnlagsdata.FradragManglerBosituasjon to "fradragsperiode_utenfor_bosituasjonperiode",
            KunneIkkeLageGrunnlagsdata.FradragForEPSMenBosituasjonUtenEPS to "fradrag_for_eps_uten_eps",
            KunneIkkeLageGrunnlagsdata.UgyldigFradragsgrunnlag(
                Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag,
            ) to "fradrag_ugyldig_fradragstype",
            KunneIkkeLageGrunnlagsdata.Konsistenssjekk(
                Konsistensproblem.BosituasjonOgFradrag.IngenBosituasjonForFradragsperiode,
            ) to "ingen_bosituasjon_for_fradragsperiode",
        ).forEach { (årsak, forventetKode) ->
            val resultat = Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(
                KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(årsak),
            ).tilResultat()

            resultat.httpCode shouldBe HttpStatusCode.BadRequest
            deserialize<ErrorJson>(resultat.json).code shouldBe forventetKode
        }
    }

    @Test
    fun `ugyldig tilstand gir bad request med konkret tilstandsmelding`() {
        val årsak = KunneIkkeOppdatereStønadsperiode.UgyldigTilstand(SimulertSøknadsbehandling::class)
        val resultat = Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(årsak).tilResultat()

        resultat.httpCode shouldBe HttpStatusCode.BadRequest
        deserialize<ErrorJson>(resultat.json) shouldBe ErrorJson(
            "Kan ikke gå fra tilstanden ${årsak.fra.simpleName} til tilstanden ${årsak.til.simpleName}",
            "ugyldig_tilstand",
        )
    }
}
