package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.brev.Avslagsgrunn
import org.junit.jupiter.api.Test

internal class BehandlingsinformasjonTest {

    @Test
    fun `alle vilkår må være innvilget for at summen av vilkår skal være innvilget`() {
        alleVilkårOppfylt.erInnvilget() shouldBe true
        alleVilkårOppfylt.erAvslag() shouldBe false
    }

    @Test
    fun `et vilkår som ikke er oppfylt fører til at summen er avslått`() {
        val info = alleVilkårOppfylt.copy(
            lovligOpphold = Behandlingsinformasjon.LovligOpphold(
                status = Behandlingsinformasjon.LovligOpphold.Status.VilkårIkkeOppfylt, begrunnelse = "får ikke lov"
            )
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
    }

    @Test
    fun `inkluderer bare den første avslagsgrunnen for vilkår som ikke er oppfylt`() {
        val info = alleVilkårOppfylt.copy(
            uførhet = Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt, uføregrad = 10, forventetInntekt = 100
            ),
            lovligOpphold = Behandlingsinformasjon.LovligOpphold(
                status = Behandlingsinformasjon.LovligOpphold.Status.VilkårIkkeOppfylt, begrunnelse = "får ikke lov"
            )
        )
        info.getAvslagsgrunn() shouldBe Avslagsgrunn.UFØRHET
    }

    @Test
    fun `ingen avslagsgrunn dersom alle vilkår er oppfylt`() {
        alleVilkårOppfylt.getAvslagsgrunn() shouldBe null
    }

    @Test
    fun `dersom uførhet er vurdert men ikke oppfylt skal man få avslag`() {
        val info = alleVilkårOppfylt.copy(
            uførhet = Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt, uføregrad = 5, forventetInntekt = 5
            )
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
        info.getAvslagsgrunn() shouldBe Avslagsgrunn.UFØRHET
    }

    @Test
    fun `dersom flyktning er vurdert men ikke oppfylt skal man få avslag`() {
        val info = alleVilkårOppfylt.copy(
            flyktning = Behandlingsinformasjon.Flyktning(
                status = Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt, begrunnelse = null
            )
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
        info.getAvslagsgrunn() shouldBe Avslagsgrunn.FLYKTNING
    }

    @Test
    fun `dersom man mangler vurdering av et vilkår er det ikke innvilget, men ikke nødvendigvis avslag`() {
        val info = alleVilkårOppfylt.copy(
            lovligOpphold = null
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe false
        info.getAvslagsgrunn() shouldBe null
    }

    private val alleVilkårOppfylt = Behandlingsinformasjon(
        uførhet = Behandlingsinformasjon.Uførhet(
            Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = 100,
            forventetInntekt = 5000
        ),
        flyktning = Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
            begrunnelse = "det stemmer"
        ),
        lovligOpphold = Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
            begrunnelse = "jepp"
        ),
        fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = "jepp"
        ),
        oppholdIUtlandet = Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
            begrunnelse = "ingen turer planlagt"
        ),
        formue = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 0,
                verdiKjøretøy = 12000,
                innskudd = 0,
                verdipapir = 0,
                pengerSkyldt = 0,
                kontanter = 1500,
                depositumskonto = 0
            ),
            ektefellesVerdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 74500,
                verdiKjøretøy = 0,
                innskudd = 13000,
                verdipapir = 2500,
                pengerSkyldt = 0,
                kontanter = 0,
                depositumskonto = 0,
            ),
            begrunnelse = "ok"
        ),
        personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = "møtte opp i går"
        ),
        bosituasjon = Behandlingsinformasjon.Bosituasjon(
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
            ektemakeEllerSamboerUnder67År = true,
            ektemakeEllerSamboerUførFlyktning = true,
            begrunnelse = "ja"
        ),
        ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(FnrGenerator.random())
    )
}
