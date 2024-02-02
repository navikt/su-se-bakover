package no.nav.su.se.bakover.domain.beregning

import beregning.domain.fradrag.FradragStrategy
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import satser.domain.SatsFactory
import satser.domain.Satsgrunn
import satser.domain.supplerendestønad.FullSupplerendeStønadForMåned
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragForMåned

sealed interface BeregningStrategy {
    val satsFactory: SatsFactory
    val sakstype: Sakstype

    fun fradragStrategy(): FradragStrategy
    fun satsgrunn(): Satsgrunn

    fun beregn(måned: Måned): FullSupplerendeStønadForMåned

    fun beregnFradrag(måned: Måned, fradrag: List<Fradrag>): List<FradragForMåned> {
        return when (sakstype) {
            Sakstype.ALDER -> fradragStrategy().beregn(fradrag, måned)[måned] ?: emptyList()
            Sakstype.UFØRE -> fradragStrategy().beregn(fradrag, måned)[måned] ?: emptyList()
        }
    }

    fun beregnFribeløpEPS(måned: Måned): Double {
        return fradragStrategy().getEpsFribeløp(måned)
    }

    data class BorAlene(
        override val satsFactory: SatsFactory,
        override val sakstype: Sakstype,
    ) : BeregningStrategy {
        override fun fradragStrategy(): FradragStrategy {
            return when (sakstype) {
                Sakstype.ALDER -> FradragStrategy.Alder.Enslig
                Sakstype.UFØRE -> FradragStrategy.Uføre.Enslig
            }
        }

        override fun satsgrunn(): Satsgrunn = Satsgrunn.ENSLIG
        override fun beregn(måned: Måned): FullSupplerendeStønadForMåned {
            return when (sakstype) {
                Sakstype.ALDER -> satsFactory.høyAlder(måned)
                Sakstype.UFØRE -> satsFactory.høyUføre(måned)
            }
        }
    }

    data class BorMedVoksne(
        override val satsFactory: SatsFactory,
        override val sakstype: Sakstype,
    ) : BeregningStrategy {
        override fun fradragStrategy(): FradragStrategy {
            return when (sakstype) {
                Sakstype.ALDER -> FradragStrategy.Alder.Enslig
                Sakstype.UFØRE -> FradragStrategy.Uføre.Enslig
            }
        }

        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
        override fun beregn(måned: Måned): FullSupplerendeStønadForMåned {
            return when (sakstype) {
                Sakstype.ALDER -> satsFactory.ordinærAlder(måned)
                Sakstype.UFØRE -> satsFactory.ordinærUføre(måned)
            }
        }
    }

    data class Eps67EllerEldre(
        override val satsFactory: SatsFactory,
        override val sakstype: Sakstype,
    ) : BeregningStrategy {
        override fun fradragStrategy(): FradragStrategy {
            return when (sakstype) {
                Sakstype.ALDER -> FradragStrategy.Alder.EpsOver67År(satsFactory)
                Sakstype.UFØRE -> FradragStrategy.Uføre.EpsOver67År(satsFactory)
            }
        }

        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE
        override fun beregn(måned: Måned): FullSupplerendeStønadForMåned {
            return when (sakstype) {
                Sakstype.ALDER -> satsFactory.ordinærAlder(måned)
                Sakstype.UFØRE -> satsFactory.ordinærUføre(måned)
            }
        }
    }

    data class EpsUnder67ÅrOgUførFlyktning(
        override val satsFactory: SatsFactory,
        override val sakstype: Sakstype,
    ) : BeregningStrategy {
        override fun fradragStrategy(): FradragStrategy {
            return when (sakstype) {
                Sakstype.ALDER -> FradragStrategy.Alder.EpsUnder67ÅrOgUførFlyktning(satsFactory)
                Sakstype.UFØRE -> FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactory)
            }
        }

        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
        override fun beregn(måned: Måned): FullSupplerendeStønadForMåned {
            return when (sakstype) {
                Sakstype.ALDER -> satsFactory.ordinærAlder(måned)
                Sakstype.UFØRE -> satsFactory.ordinærUføre(måned)
            }
        }
    }

    data class EpsUnder67År(
        override val satsFactory: SatsFactory,
        override val sakstype: Sakstype,
    ) : BeregningStrategy {
        override fun fradragStrategy(): FradragStrategy {
            return when (sakstype) {
                Sakstype.ALDER -> FradragStrategy.Alder.EpsUnder67År
                Sakstype.UFØRE -> FradragStrategy.Uføre.EpsUnder67År
            }
        }

        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
        override fun beregn(måned: Måned): FullSupplerendeStønadForMåned {
            return when (sakstype) {
                Sakstype.ALDER -> satsFactory.høyAlder(måned)
                Sakstype.UFØRE -> satsFactory.høyUføre(måned)
            }
        }
    }
}

fun Bosituasjon.Fullstendig.utledBeregningsstrategi(
    satsFactory: SatsFactory,
    sakstype: Sakstype,
): BeregningStrategy {
    return when (this) {
        is Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> BeregningStrategy.BorMedVoksne(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> BeregningStrategy.EpsUnder67År(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> BeregningStrategy.Eps67EllerEldre(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
        is Bosituasjon.Fullstendig.Enslig -> BeregningStrategy.BorAlene(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
    }
}
