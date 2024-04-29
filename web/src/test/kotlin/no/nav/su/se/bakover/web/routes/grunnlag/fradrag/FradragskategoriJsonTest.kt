package no.nav.su.se.bakover.web.routes.grunnlag.fradrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.web.routes.grunnlag.fradrag.FradragskategoriJson.Companion.toJson
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.Fradragstype

class FradragskategoriJsonTest {

    @Test
    fun `mapper domene-type til json-type`() {
        Fradragstype.Kategori.Alderspensjon.toJson() shouldBe FradragskategoriJson.Alderspensjon
        Fradragstype.Kategori.Annet.toJson() shouldBe FradragskategoriJson.Annet
        Fradragstype.Kategori.Arbeidsavklaringspenger.toJson() shouldBe FradragskategoriJson.Arbeidsavklaringspenger
        Fradragstype.Kategori.Arbeidsinntekt.toJson() shouldBe FradragskategoriJson.Arbeidsinntekt
        Fradragstype.Kategori.AvtalefestetPensjon.toJson() shouldBe FradragskategoriJson.AvtalefestetPensjon
        Fradragstype.Kategori.AvtalefestetPensjonPrivat.toJson() shouldBe FradragskategoriJson.AvtalefestetPensjonPrivat
        Fradragstype.Kategori.BidragEtterEkteskapsloven.toJson() shouldBe FradragskategoriJson.BidragEtterEkteskapsloven
        Fradragstype.Kategori.Dagpenger.toJson() shouldBe FradragskategoriJson.Dagpenger
        Fradragstype.Kategori.Fosterhjemsgodtgjørelse.toJson() shouldBe FradragskategoriJson.Fosterhjemsgodtgjørelse
        Fradragstype.Kategori.Gjenlevendepensjon.toJson() shouldBe FradragskategoriJson.Gjenlevendepensjon
        Fradragstype.Kategori.Introduksjonsstønad.toJson() shouldBe FradragskategoriJson.Introduksjonsstønad
        Fradragstype.Kategori.Kapitalinntekt.toJson() shouldBe FradragskategoriJson.Kapitalinntekt
        Fradragstype.Kategori.Kontantstøtte.toJson() shouldBe FradragskategoriJson.Kontantstøtte
        Fradragstype.Kategori.Kvalifiseringsstønad.toJson() shouldBe FradragskategoriJson.Kvalifiseringsstønad
        Fradragstype.Kategori.NAVytelserTilLivsopphold.toJson() shouldBe FradragskategoriJson.NAVytelserTilLivsopphold
        Fradragstype.Kategori.OffentligPensjon.toJson() shouldBe FradragskategoriJson.OffentligPensjon
        Fradragstype.Kategori.PrivatPensjon.toJson() shouldBe FradragskategoriJson.PrivatPensjon
        Fradragstype.Kategori.Sosialstønad.toJson() shouldBe FradragskategoriJson.Sosialstønad
        Fradragstype.Kategori.StatensLånekasse.toJson() shouldBe FradragskategoriJson.StatensLånekasse
        Fradragstype.Kategori.SupplerendeStønad.toJson() shouldBe FradragskategoriJson.SupplerendeStønad
        Fradragstype.Kategori.Sykepenger.toJson() shouldBe FradragskategoriJson.Sykepenger
        Fradragstype.Kategori.Tiltakspenger.toJson() shouldBe FradragskategoriJson.Tiltakspenger
        Fradragstype.Kategori.Ventestønad.toJson() shouldBe FradragskategoriJson.Ventestønad
        Fradragstype.Kategori.Uføretrygd.toJson() shouldBe FradragskategoriJson.Uføretrygd
        Fradragstype.Kategori.ForventetInntekt.toJson() shouldBe FradragskategoriJson.ForventetInntekt
        Fradragstype.Kategori.AvkortingUtenlandsopphold.toJson() shouldBe FradragskategoriJson.AvkortingUtenlandsopphold
        Fradragstype.Kategori.BeregnetFradragEPS.toJson() shouldBe FradragskategoriJson.BeregnetFradragEPS
        Fradragstype.Kategori.UnderMinstenivå.toJson() shouldBe FradragskategoriJson.UnderMinstenivå
    }
}
