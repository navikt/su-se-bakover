package vilkår.common.domain

import arrow.core.Nel
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.erSortert
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

    override val perioder: Nel<Periode> get() = vurderingsperioder.minsteAntallSammenhengendePerioder()

    override fun hentTidligesteDatoForAvslag(): LocalDate? {
        return vurderingsperioder.filter { it.vurdering == Vurdering.Avslag }.map { it.periode.fraOgMed }
            .minByOrNull { it }
    }

    fun minsteAntallSammenhengendePerioder(): List<Periode> {
        return vurderingsperioder.minsteAntallSammenhengendePerioder()
    }
}

/**
 * Skal kunne kalles fra undertyper av [Vilkår].
 */
fun VurdertVilkår.kastHvisPerioderErUsortertEllerHarDuplikater() {
    require(perioder.erSortert())
    require(!perioder.harDuplikater())
    // TODO jah: Vurder å legg på require(perioder.minsteAntallSammenhengendePerioder() == perioder)
}
