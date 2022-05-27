package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class PersonligOppmøteTest {

    @Test
    fun `er ikke ferdigbehandlet hvis status er uavklart`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.Uavklart,
        ).let {
            it.erVilkårOppfylt() shouldBe false
            it.erVilkårIkkeOppfylt() shouldBe false
        }
    }

    @Test
    fun `er ikke oppfylt så lenge man ikke har møtt selv`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
        ).erVilkårOppfylt() shouldBe false
    }

    @Test
    fun `er oppfylt så lenge man har en god grunn til å ikke ha møtt selv`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenVerge,
        ).erVilkårOppfylt() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenKortvarigSykMedLegeerklæring,
        ).erVilkårOppfylt() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
        ).erVilkårOppfylt() shouldBe true

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenSykMedLegeerklæringOgFullmakt,
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `er oppylt så lenge man har møtt selv`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
        ).erVilkårOppfylt() shouldBe true
    }
}
