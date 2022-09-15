package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.grunnlag.Grunnlag

enum class Satsgrunn {
    ENSLIG,
    DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING,
}

fun Grunnlag.Bosituasjon.Fullstendig.satsgrunn(): Satsgrunn {
    return when (this) {
        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
        is Grunnlag.Bosituasjon.Fullstendig.Enslig -> Satsgrunn.ENSLIG
    }
}
