package vilkår.common.domain

import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder
import java.time.LocalDate

interface IkkeVurdertVilkår : Vilkår {
    override val vurdering: Vurdering.Uavklart get() = Vurdering.Uavklart
    override val erAvslag: Boolean get() = false
    override val erInnvilget: Boolean get() = false
    override val perioderSlåttSammen: EmptyPerioder get() = EmptyPerioder
    override val perioderIkkeSlåttSammen: EmptyPerioder get() = EmptyPerioder
    override val avslagsgrunner: List<Avslagsgrunn> get() = emptyList()
    override fun hentTidligesteDatoForAvslag(): LocalDate? = null
    override fun copyWithNewId(): IkkeVurdertVilkår = this
}
