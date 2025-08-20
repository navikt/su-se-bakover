package no.nav.su.se.bakover.web.routes.grunnlag.fradrag

import vilkår.inntekt.domain.grunnlag.Fradragstype

internal enum class FradragskategoriJson {
    Alderspensjon,
    Annet,
    Omstillingsstønad,
    Arbeidsavklaringspenger,
    Arbeidsinntekt,
    AvtalefestetPensjon,
    AvtalefestetPensjonPrivat,
    BidragEtterEkteskapsloven,
    Dagpenger,
    Fosterhjemsgodtgjørelse,
    Gjenlevendepensjon,
    Introduksjonsstønad,
    Kapitalinntekt,
    Kontantstøtte,
    Kvalifiseringsstønad,
    NAVytelserTilLivsopphold,
    OffentligPensjon,
    PrivatPensjon,
    Sosialstønad,
    StatensLånekasse,
    SupplerendeStønad,
    Sykepenger,
    Tiltakspenger,
    Ventestønad,
    Uføretrygd,
    ForventetInntekt,
    AvkortingUtenlandsopphold,
    BeregnetFradragEPS,
    UnderMinstenivå,
    ;

    companion object {
        fun Fradragstype.Kategori.toJson(): FradragskategoriJson = when (this) {
            Fradragstype.Kategori.Alderspensjon -> Alderspensjon
            Fradragstype.Kategori.Annet -> Annet
            Fradragstype.Kategori.Omstillingsstønad -> Omstillingsstønad
            Fradragstype.Kategori.Arbeidsavklaringspenger -> Arbeidsavklaringspenger
            Fradragstype.Kategori.Arbeidsinntekt -> Arbeidsinntekt
            Fradragstype.Kategori.AvkortingUtenlandsopphold -> AvkortingUtenlandsopphold
            Fradragstype.Kategori.AvtalefestetPensjon -> AvtalefestetPensjon
            Fradragstype.Kategori.AvtalefestetPensjonPrivat -> AvtalefestetPensjonPrivat
            Fradragstype.Kategori.BeregnetFradragEPS -> BeregnetFradragEPS
            Fradragstype.Kategori.BidragEtterEkteskapsloven -> BidragEtterEkteskapsloven
            Fradragstype.Kategori.Dagpenger -> Dagpenger
            Fradragstype.Kategori.ForventetInntekt -> ForventetInntekt
            Fradragstype.Kategori.Fosterhjemsgodtgjørelse -> Fosterhjemsgodtgjørelse
            Fradragstype.Kategori.Gjenlevendepensjon -> Gjenlevendepensjon
            Fradragstype.Kategori.Introduksjonsstønad -> Introduksjonsstønad
            Fradragstype.Kategori.Kapitalinntekt -> Kapitalinntekt
            Fradragstype.Kategori.Kontantstøtte -> Kontantstøtte
            Fradragstype.Kategori.Kvalifiseringsstønad -> Kvalifiseringsstønad
            Fradragstype.Kategori.NAVytelserTilLivsopphold -> NAVytelserTilLivsopphold
            Fradragstype.Kategori.OffentligPensjon -> OffentligPensjon
            Fradragstype.Kategori.PrivatPensjon -> PrivatPensjon
            Fradragstype.Kategori.Sosialstønad -> Sosialstønad
            Fradragstype.Kategori.StatensLånekasse -> StatensLånekasse
            Fradragstype.Kategori.SupplerendeStønad -> SupplerendeStønad
            Fradragstype.Kategori.Sykepenger -> Sykepenger
            Fradragstype.Kategori.Tiltakspenger -> Tiltakspenger
            Fradragstype.Kategori.Uføretrygd -> Uføretrygd
            Fradragstype.Kategori.UnderMinstenivå -> UnderMinstenivå
            Fradragstype.Kategori.Ventestønad -> Ventestønad
        }
    }
}
