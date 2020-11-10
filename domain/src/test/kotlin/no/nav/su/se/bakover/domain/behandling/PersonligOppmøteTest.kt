package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.Avslagsgrunn
import org.junit.jupiter.api.Test

internal class PersonligOppmøteTest {
    @Test
    fun `er gyldig uansett hva man putter inn`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = null
        ).erGyldig() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenVerge,
            begrunnelse = "adsad"
        ).erGyldig() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
            begrunnelse = null
        ).erGyldig() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenKortvarigSykMedLegeerklæring,
            begrunnelse = null
        ).erGyldig() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
            begrunnelse = null
        ).erGyldig() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenSykMedLegeerklæringOgFullmakt,
            begrunnelse = null
        ).erGyldig() shouldBe true
    }

    @Test
    fun `er ferdigbehandlet uansett hva man putter inn`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = null
        ).erFerdigbehandlet() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenVerge,
            begrunnelse = "adsad"
        ).erFerdigbehandlet() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
            begrunnelse = null
        ).erFerdigbehandlet() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenKortvarigSykMedLegeerklæring,
            begrunnelse = null
        ).erFerdigbehandlet() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
            begrunnelse = null
        ).erFerdigbehandlet() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenSykMedLegeerklæringOgFullmakt,
            begrunnelse = null
        ).erFerdigbehandlet() shouldBe true
    }

    @Test
    fun `er ikke oppfylt så lenge man ikke har møtt selv`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
            begrunnelse = null
        ).erVilkårOppfylt() shouldBe false
    }

    @Test
    fun `er oppfylt så lenge man har en god grunn til å ikke ha møtt selv`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenVerge,
            begrunnelse = null
        ).erVilkårOppfylt() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenKortvarigSykMedLegeerklæring,
            begrunnelse = null
        ).erVilkårOppfylt() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
            begrunnelse = null
        ).erVilkårOppfylt() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenSykMedLegeerklæringOgFullmakt,
            begrunnelse = null
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `er oppylt så lenge man har møtt selv`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = null
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `avslagsgrunn er personlig oppmøte dersom man ikker har møtt personlig`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
            begrunnelse = null
        ).avslagsgrunn() shouldBe Avslagsgrunn.PERSONLIG_OPPMØTE
    }

    @Test
    fun `avslagsgrunn er null i andre tilfeller`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = null
        ).avslagsgrunn() shouldBe null

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
            begrunnelse = null
        ).avslagsgrunn() shouldBe null
    }
}
