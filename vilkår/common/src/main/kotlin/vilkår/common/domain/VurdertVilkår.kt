package vilkår.common.domain

import arrow.core.Nel
import no.nav.su.se.bakover.common.domain.tid.periode.NonEmptyIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tid.periode.NonEmptySlåttSammenIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.tid.periode.erSortertPåFraOgMed
import no.nav.su.se.bakover.common.tid.periode.harDuplikater
import java.time.LocalDate

interface VurdertVilkår : Vilkår {
    val vurderingsperioder: Nel<Vurderingsperiode>

    override val erInnvilget: Boolean get() = vurderingsperioder.all { it.vurdering == Vurdering.Innvilget }

    override val erAvslag: Boolean get() = vurderingsperioder.any { it.vurdering == Vurdering.Avslag }

    override val vurdering: Vurdering
        get() = when {
            erInnvilget -> Vurdering.Innvilget
            erAvslag -> Vurdering.Avslag
            else -> Vurdering.Uavklart
        }

    override val perioderSlåttSammen: NonEmptySlåttSammenIkkeOverlappendePerioder get() = vurderingsperioder.minsteAntallSammenhengendePerioder()
    override val perioderIkkeSlåttSammen: NonEmptyIkkeOverlappendePerioder
        get() = NonEmptyIkkeOverlappendePerioder.create(
            vurderingsperioder.map { it.periode },
        )

    override fun hentTidligesteDatoForAvslag(): LocalDate? {
        return vurderingsperioder.filter { it.vurdering == Vurdering.Avslag }.map { it.periode.fraOgMed }
            .minByOrNull { it }
    }

    fun minsteAntallSammenhengendePerioder(): NonEmptySlåttSammenIkkeOverlappendePerioder {
        return vurderingsperioder.minsteAntallSammenhengendePerioder()
    }

    override fun copyWithNewId(): VurdertVilkår
}

/**
 * Skal kunne kalles fra undertyper av [Vilkår].
 */
fun VurdertVilkår.kastHvisPerioderErUsortertEllerHarDuplikater() {
    require(!perioderSlåttSammen.harDuplikater())
    require(perioderSlåttSammen.erSortertPåFraOgMed())

    // TODO jah: Vurder å legg på require(perioder.minsteAntallSammenhengendePerioder() == perioder)
}
