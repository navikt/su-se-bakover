package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.FnrGenerator
import org.junit.jupiter.api.Test

internal class EktefelleTest {
    @Test
    fun `er gyldig uansett hva man putter inn`() {
        Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(FnrGenerator.random()).erGyldig() shouldBe true
        Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle.erGyldig() shouldBe true
    }

    @Test
    fun `er ferdigbehandlet uansett hva man putter inn`() {
        Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(FnrGenerator.random())
            .erFerdigbehandlet() shouldBe true
        Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle
            .erFerdigbehandlet() shouldBe true
    }

    @Test
    fun `er oppfylt uansett hva man putter inn`() {
        Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(FnrGenerator.random()).erVilkårOppfylt() shouldBe true
        Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle.erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `har ingen avslagsgrunn`() {
        Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(FnrGenerator.random()).avslagsgrunn() shouldBe null
        Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle.avslagsgrunn() shouldBe null
    }
}
