package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import org.junit.jupiter.api.Test

internal class FormueTest {
    @Test
    fun `er gyldig dersom man må innhente mer informasjon`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.MåInnhenteMerInformasjon,
            verdier = null,
            borSøkerMedEPS = false,
            ektefellesVerdier = null,
            begrunnelse = null
        ).erGyldig() shouldBe true
    }

    @Test
    fun `er ugyldig dersom status er oppfylt men brukers verdier mangler`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = null,
            borSøkerMedEPS = false,
            ektefellesVerdier = null,
            begrunnelse = "null"
        ).erGyldig() shouldBe false
    }

    @Test
    fun `er ugyldig dersom status er oppfylt men brukers verdier mangler enkeltfelter1`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            borSøkerMedEPS = false,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = null,
                verdiKjøretøy = 100,
                innskudd = 100,
                verdipapir = 100,
                pengerSkyldt = 100,
                kontanter = 100,
                depositumskonto = 100
            ),
            ektefellesVerdier = null,
            begrunnelse = "null"
        ).erGyldig() shouldBe false
    }

    @Test
    fun `er ugyldig dersom status er oppfylt men brukers verdier mangler enkeltfelter2`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            borSøkerMedEPS = false,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 100,
                verdiKjøretøy = 100,
                innskudd = 100,
                verdipapir = 100,
                pengerSkyldt = 100,
                kontanter = 100,
                depositumskonto = null
            ),
            ektefellesVerdier = null,
            begrunnelse = "null"
        ).erGyldig() shouldBe false
    }

    @Test
    fun `er gyldig dersom status er oppfylt men ektefelles verdier mangler`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            borSøkerMedEPS = false,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 100,
                verdiKjøretøy = 100,
                innskudd = 100,
                verdipapir = 100,
                pengerSkyldt = 100,
                kontanter = 100,
                depositumskonto = 100
            ),
            ektefellesVerdier = null,
            begrunnelse = "null"
        ).erGyldig() shouldBe true
    }

    @Test
    fun `er gyldig dersom status er oppfylt men ektefelles verdier er med mangler`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            borSøkerMedEPS = true,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 100,
                verdiKjøretøy = 100,
                innskudd = 100,
                verdipapir = 100,
                pengerSkyldt = 100,
                kontanter = 100,
                depositumskonto = 100
            ),
            ektefellesVerdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 100,
                verdiKjøretøy = 100,
                innskudd = 100,
                verdipapir = 100,
                pengerSkyldt = 100,
                kontanter = 100,
                depositumskonto = 100
            ),
            begrunnelse = "null"
        ).erGyldig() shouldBe true
    }

    @Test
    fun `er ikke ferdigbehandlet dersom status er må innhente er informasjon`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.MåInnhenteMerInformasjon,
            verdier = null,
            borSøkerMedEPS = false,
            ektefellesVerdier = null,
            begrunnelse = "null"
        ).let {
            it.erVilkårOppfylt() shouldBe false
            it.erVilkårIkkeOppfylt() shouldBe false
        }
    }

    @Test
    fun `vilkår er oppfylt dersom status er oppfylt`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = null,
            borSøkerMedEPS = false,
            ektefellesVerdier = null,
            begrunnelse = "null"
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `vilkår er ikke oppfylt dersom status ikke er oppfylt`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt,
            verdier = null,
            borSøkerMedEPS = false,
            ektefellesVerdier = null,
            begrunnelse = "null"
        ).erVilkårOppfylt() shouldBe false

        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.MåInnhenteMerInformasjon,
            verdier = null,
            borSøkerMedEPS = false,
            ektefellesVerdier = null,
            begrunnelse = "null"
        ).erVilkårOppfylt() shouldBe false
    }

    @Test
    fun `avslagsgrunn er formue dersom vilkår ikke er oppfylt`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt,
            verdier = null,
            borSøkerMedEPS = false,
            ektefellesVerdier = null,
            begrunnelse = "null"
        ).avslagsgrunn() shouldBe Avslagsgrunn.FORMUE
    }

    @Test
    fun `avslagsgrunn er null dersom vilkår er oppfylt`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = null,
            borSøkerMedEPS = false,
            ektefellesVerdier = null,
            begrunnelse = "null"
        ).avslagsgrunn() shouldBe null
    }
}
