package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.person.Fnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FormueTest {

    @Test
    fun `er ikke ferdigbehandlet dersom status er må innhente er informasjon`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.MåInnhenteMerInformasjon,
            verdier = Behandlingsinformasjon.Formue.Verdier.lagTomVerdier(),
            epsVerdier = null,
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
            verdier = Behandlingsinformasjon.Formue.Verdier.lagTomVerdier(),
            epsVerdier = null,
            begrunnelse = "null"
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `vilkår er ikke oppfylt dersom status ikke er oppfylt`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt,
            verdier = Behandlingsinformasjon.Formue.Verdier.lagTomVerdier(),
            epsVerdier = null,
            begrunnelse = "null",
        ).erVilkårOppfylt() shouldBe false

        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.MåInnhenteMerInformasjon,
            verdier = Behandlingsinformasjon.Formue.Verdier.lagTomVerdier(),
            epsVerdier = null,
            begrunnelse = "null",
        ).erVilkårOppfylt() shouldBe false
    }

    @Test
    fun `nullstiller formue for eps hvis det ikke er EPS i bosituasjon`() {
        val f = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = Behandlingsinformasjon.Formue.Verdier.lagTomVerdier(),
            epsVerdier = Behandlingsinformasjon.Formue.Verdier.lagTomVerdier(),
            begrunnelse = null,
        )

        val bosituasjonUtenEPS = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),

        )

        f.nullstillEpsFormueHvisIngenEps(bosituasjonUtenEPS) shouldBe f.copy(
            epsVerdier = null,
        )
    }

    @Test
    fun `nullstiller ikke formue for eps hvis det finnes eps`() {
        val f = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = Behandlingsinformasjon.Formue.Verdier.lagTomVerdier(),
            epsVerdier = Behandlingsinformasjon.Formue.Verdier.lagTomVerdier(),
            begrunnelse = null,
        )

        val bosituasjonUtenEPS = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            fnr = Fnr.generer(),
        )

        f.nullstillEpsFormueHvisIngenEps(bosituasjonUtenEPS) shouldBe f
    }
}
