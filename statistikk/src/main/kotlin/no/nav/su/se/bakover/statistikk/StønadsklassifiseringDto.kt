package no.nav.su.se.bakover.statistikk

import no.nav.su.se.bakover.domain.grunnlag.Bosituasjon

/**
 * Brukes på tvers av stønadsstatistikk og behandlingsstatistikk.
 */
internal enum class StønadsklassifiseringDto(val beskrivelse: String) {
    BOR_ALENE("Bor alene"),
    BOR_MED_ANDRE_VOKSNE("Bor med andre voksne"),
    BOR_MED_EKTEFELLE_UNDER_67_IKKE_UFØR_FLYKTNING("Bor med ektefelle under 67 år, ikke ufør flyktning"),
    BOR_MED_EKTEFELLE_OVER_67("Bor med ektefelle over 67 år"),
    BOR_MED_EKTEFELLE_UNDER_67_UFØR_FLYKTNING("Bor med ektefelle under 67 år, ufør flyktning"),
    UFULLSTENDIG_GRUNNLAG("Mangler grunnlag for å avgjøre stønadsklassifisering"),
    ;

    companion object {
        fun Bosituasjon.Fullstendig.stønadsklassifisering(): StønadsklassifiseringDto {
            return when (this) {
                is Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> BOR_MED_ANDRE_VOKSNE
                is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> BOR_MED_EKTEFELLE_UNDER_67_IKKE_UFØR_FLYKTNING
                is Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> BOR_MED_EKTEFELLE_OVER_67
                is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> BOR_MED_EKTEFELLE_UNDER_67_UFØR_FLYKTNING
                is Bosituasjon.Fullstendig.Enslig -> BOR_ALENE
            }
        }
    }
}
