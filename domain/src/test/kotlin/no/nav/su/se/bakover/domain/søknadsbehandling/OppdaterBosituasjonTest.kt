package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.harEpsInntekt
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataMedEpsMedFradrag
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerSøknadsbehandlingInnvilget
import org.junit.jupiter.api.Test

internal class OppdaterBosituasjonTest {
    @Test
    fun `fjerner formue og fradrag for eps dersom hvis bosituasjon oppdateres til enslig`() {
        val bosituasjon = bosituasjongrunnlagEpsUførFlyktning()
        val customGrunnlag = grunnlagsdataMedEpsMedFradrag(
            bosituasjon = nonEmptyListOf(bosituasjon),
        ).let { listOf(it.bosituasjon, it.fradragsgrunnlag) }.flatten()
        val customVilkår = vilkårsvurderingerSøknadsbehandlingInnvilget(
            bosituasjon = nonEmptyListOf(bosituasjon),
            formue = formuevilkårMedEps0Innvilget(
                bosituasjon = nonEmptyListOf(bosituasjon),
            ),
        ).vilkår.toList()
        søknadsbehandlingVilkårsvurdertInnvilget(
            customGrunnlag = customGrunnlag,
            customVilkår = customVilkår,
        ).second.let { original ->
            original.grunnlagsdata.fradragsgrunnlag.harEpsInntekt() shouldBe true
            original.vilkårsvurderinger.formue.harEPSFormue() shouldBe true

            original.oppdaterBosituasjon(
                bosituasjon = bosituasjongrunnlagEnslig(),
                saksbehandler = saksbehandler,
                hendelse = Søknadsbehandlingshendelse(
                    tidspunkt = fixedTidspunkt,
                    saksbehandler = saksbehandler,
                    handling = SøknadsbehandlingsHandling.TattStillingTilEPS,
                ),
            ).getOrFail().also {
                it.grunnlagsdata.fradragsgrunnlag.harEpsInntekt() shouldBe false
                it.vilkårsvurderinger.formue.harEPSFormue() shouldBe false
            }
        }
    }
}
