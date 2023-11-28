package no.nav.su.se.bakover.domain.beregning

import arrow.core.getOrElse
import behandling.domain.beregning.fradrag.Fradrag
import behandling.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import satser.domain.SatsFactory
import satser.domain.supplerendestønad.FullSupplerendeStønadForMåned
import java.time.Clock

data class Beregningsperiode(
    private val periode: Periode,
    private val strategy: BeregningStrategy,
) {
    fun periode(): Periode {
        return periode
    }

    fun månedsoversikt(): Map<Måned, BeregningStrategy> {
        return periode.måneder().associateWith { strategy }
    }
}

class BeregningStrategyFactory(
    val clock: Clock,
    val satsFactory: SatsFactory,
) {
    fun beregn(revurdering: Revurdering): Beregning {
        return beregn(
            grunnlagsdataOgVilkårsvurderinger = revurdering.grunnlagsdataOgVilkårsvurderinger,
            begrunnelse = null,
            sakstype = revurdering.sakstype,
        )
    }

    fun beregn(regulering: Regulering, begrunnelse: String?): Beregning {
        return beregn(
            grunnlagsdataOgVilkårsvurderinger = regulering.grunnlagsdataOgVilkårsvurderinger,
            begrunnelse = begrunnelse,
            sakstype = regulering.sakstype,
        )
    }

    fun beregn(
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger,
        begrunnelse: String?,
        sakstype: Sakstype,
    ): Beregning {
        val totalBeregningsperiode = grunnlagsdataOgVilkårsvurderinger.periode()!!

        require(grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.isNotEmpty()) { "Bosituasjon er påkrevet for å kunne beregne." }

        val delperioder = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.map {
            Beregningsperiode(
                periode = it.periode,
                strategy = (it as Grunnlag.Bosituasjon.Fullstendig).utledBeregningsstrategi(satsFactory, sakstype),
            )
        }

        val fradrag = when (sakstype) {
            Sakstype.ALDER -> {
                grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag
            }
            Sakstype.UFØRE -> {
                Beregningsgrunnlag.tryCreate(
                    beregningsperiode = totalBeregningsperiode,
                    uføregrunnlag = when (
                        val vilkårsvurderinger =
                            grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
                    ) {
                        is Vilkårsvurderinger.Revurdering.Uføre -> {
                            vilkårsvurderinger.uføre.grunnlag
                        }

                        is Vilkårsvurderinger.Søknadsbehandling.Uføre -> {
                            vilkårsvurderinger.uføre.grunnlag
                        }

                        is Vilkårsvurderinger.Revurdering.Alder -> {
                            throw IllegalStateException("Uføresak med vilkårsvurderinger for alder!")
                        }

                        is Vilkårsvurderinger.Søknadsbehandling.Alder -> {
                            throw IllegalStateException("Uføresak med vilkårsvurderinger for alder!")
                        }
                    },
                    fradragFraSaksbehandler = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag,
                ).getOrElse {
                    // TODO jah: Kan vurdere å legge på en left her (KanIkkeBeregne.UgyldigBeregningsgrunnlag
                    throw IllegalArgumentException(it.toString())
                }.fradrag
            }
        }

        require(totalBeregningsperiode.fullstendigOverlapp(delperioder.map { it.periode() }))

        return BeregningFactory(clock).ny(
            fradrag = fradrag,
            begrunnelse = begrunnelse,
            beregningsperioder = delperioder,
        )
    }
}

sealed class BeregningStrategy {
    protected abstract val satsFactory: SatsFactory
    protected abstract val sakstype: Sakstype

    abstract fun fradragStrategy(): FradragStrategy
    abstract fun satsgrunn(): Satsgrunn

    abstract fun beregn(måned: Måned): FullSupplerendeStønadForMåned

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
    ) : BeregningStrategy() {
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
    ) : BeregningStrategy() {
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
    ) : BeregningStrategy() {
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
    ) : BeregningStrategy() {
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
    ) : BeregningStrategy() {
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

fun Grunnlag.Bosituasjon.Fullstendig.utledBeregningsstrategi(
    satsFactory: SatsFactory,
    sakstype: Sakstype,
): BeregningStrategy {
    return when (this) {
        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> BeregningStrategy.BorMedVoksne(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> BeregningStrategy.EpsUnder67År(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> BeregningStrategy.Eps67EllerEldre(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
        is Grunnlag.Bosituasjon.Fullstendig.Enslig -> BeregningStrategy.BorAlene(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
    }
}
