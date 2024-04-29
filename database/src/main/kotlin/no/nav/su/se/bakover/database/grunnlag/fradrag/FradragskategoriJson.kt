package no.nav.su.se.bakover.database.grunnlag.fradrag

import vilkår.inntekt.domain.grunnlag.Fradragstype

enum class FradragskategoriJson {
    Alderspensjon,
    Annet,
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

    fun toDomain(): Fradragstype.Kategori = when (this) {
        Alderspensjon -> Fradragstype.Kategori.Alderspensjon
        Annet -> Fradragstype.Kategori.Annet
        Arbeidsavklaringspenger -> Fradragstype.Kategori.Arbeidsavklaringspenger
        Arbeidsinntekt -> Fradragstype.Kategori.Arbeidsinntekt
        AvkortingUtenlandsopphold -> Fradragstype.Kategori.AvkortingUtenlandsopphold
        AvtalefestetPensjon -> Fradragstype.Kategori.AvtalefestetPensjon
        AvtalefestetPensjonPrivat -> Fradragstype.Kategori.AvtalefestetPensjonPrivat
        BeregnetFradragEPS -> Fradragstype.Kategori.BeregnetFradragEPS
        BidragEtterEkteskapsloven -> Fradragstype.Kategori.BidragEtterEkteskapsloven
        Dagpenger -> Fradragstype.Kategori.Dagpenger
        ForventetInntekt -> Fradragstype.Kategori.ForventetInntekt
        Fosterhjemsgodtgjørelse -> Fradragstype.Kategori.Fosterhjemsgodtgjørelse
        Gjenlevendepensjon -> Fradragstype.Kategori.Gjenlevendepensjon
        Introduksjonsstønad -> Fradragstype.Kategori.Introduksjonsstønad
        Kapitalinntekt -> Fradragstype.Kategori.Kapitalinntekt
        Kontantstøtte -> Fradragstype.Kategori.Kontantstøtte
        Kvalifiseringsstønad -> Fradragstype.Kategori.Kvalifiseringsstønad
        NAVytelserTilLivsopphold -> Fradragstype.Kategori.NAVytelserTilLivsopphold
        OffentligPensjon -> Fradragstype.Kategori.OffentligPensjon
        PrivatPensjon -> Fradragstype.Kategori.PrivatPensjon
        Sosialstønad -> Fradragstype.Kategori.Sosialstønad
        StatensLånekasse -> Fradragstype.Kategori.StatensLånekasse
        SupplerendeStønad -> Fradragstype.Kategori.SupplerendeStønad
        Sykepenger -> Fradragstype.Kategori.Sykepenger
        Tiltakspenger -> Fradragstype.Kategori.Tiltakspenger
        Uføretrygd -> Fradragstype.Kategori.Uføretrygd
        UnderMinstenivå -> Fradragstype.Kategori.UnderMinstenivå
        Ventestønad -> Fradragstype.Kategori.Ventestønad
    }

    companion object {
        fun Fradragstype.Kategori.toDbJson(): FradragskategoriJson = when (this) {
            Fradragstype.Kategori.Alderspensjon -> Alderspensjon
            Fradragstype.Kategori.Annet -> Annet
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
