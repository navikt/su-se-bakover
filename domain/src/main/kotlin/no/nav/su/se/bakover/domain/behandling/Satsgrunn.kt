package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.grunnlag.Bosituasjon

enum class Satsgrunn {
    ENSLIG,
    DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING,
}

fun Bosituasjon.Fullstendig.satsgrunn(): Satsgrunn {
    return when (this) {
        is Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE
        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
        is Bosituasjon.Fullstendig.Enslig -> Satsgrunn.ENSLIG
    }
}
