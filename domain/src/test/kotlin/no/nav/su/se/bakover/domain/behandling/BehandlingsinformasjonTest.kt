package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.Avslagsgrunn
import org.junit.jupiter.api.Test

internal class BehandlingsinformasjonTest {
    private val behandlingsinformasjon = Behandlingsinformasjon(
        uførhet = Behandlingsinformasjon.Uførhet(
            Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = null,
            forventetInntekt = null
        ),
        flyktning = Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
            begrunnelse = null
        ),
        lovligOpphold = Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
            begrunnelse = null
        ),
        fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = null
        ),
        oppholdIUtlandet = Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
            begrunnelse = null
        ),
        formue = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = null,
                verdiKjøretøy = null,
                innskudd = null,
                verdipapir = null,
                pengerSkyldt = null,
                kontanter = null,
                depositumskonto = null
            ),
            ektefellesVerdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = null,
                verdiKjøretøy = null,
                innskudd = null,
                verdipapir = null,
                pengerSkyldt = null,
                kontanter = null,
                depositumskonto = null,
            ),
            begrunnelse = null
        ),
        personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = null
        ),
        bosituasjon = Behandlingsinformasjon.Bosituasjon(
            delerBolig = false,
            delerBoligMed = null,
            ektemakeEllerSamboerUnder67År = null,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        )
    )

    @Test
    fun `ikke oppfylt uførhet`() {
        behandlingsinformasjon.copy(
            uførhet = behandlingsinformasjon.uførhet!!.copy(status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt)
        ).getAvslagsgrunn() shouldBe Avslagsgrunn.UFØRHET
    }

    @Test
    fun `ikke oppfylt flyktning`() {
        behandlingsinformasjon.copy(
            flyktning = behandlingsinformasjon.flyktning!!.copy(status = Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt)
        ).getAvslagsgrunn() shouldBe Avslagsgrunn.FLYKTNING
    }

    @Test
    fun `ikke oppfylt lovlig opphold`() {
        behandlingsinformasjon.copy(
            lovligOpphold = behandlingsinformasjon.lovligOpphold!!.copy(status = Behandlingsinformasjon.LovligOpphold.Status.VilkårIkkeOppfylt)
        ).getAvslagsgrunn() shouldBe Avslagsgrunn.OPPHOLDSTILLATELSE
    }

    @Test
    fun `ikke fast opphold i norge`() {
        behandlingsinformasjon.copy(
            fastOppholdINorge = behandlingsinformasjon.fastOppholdINorge!!.copy(status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt)
        ).getAvslagsgrunn() shouldBe Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
    }

    @Test
    fun `opphold i utlandet ikke oppfylt`() {
        behandlingsinformasjon.copy(
            oppholdIUtlandet = behandlingsinformasjon.oppholdIUtlandet!!.copy(status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet)
        ).getAvslagsgrunn() shouldBe Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
    }

    @Test
    fun `formue ikke oppfylt`() {
        behandlingsinformasjon.copy(
            formue = behandlingsinformasjon.formue!!.copy(status = Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt)
        ).getAvslagsgrunn() shouldBe Avslagsgrunn.FORMUE
    }

    @Test
    fun `personlig oppmøte ikke møtt`() {
        behandlingsinformasjon.copy(
            personligOppmøte = behandlingsinformasjon.personligOppmøte!!.copy(status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig)
        ).getAvslagsgrunn() shouldBe Avslagsgrunn.PERSONLIG_OPPMØTE
    }
}
