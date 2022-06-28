package no.nav.su.se.bakover.domain.vilkår

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

/**
 * Et [Vilkår], dersom det er vurdert, er delt opp i 1 eller flere [Vurderingsperiode].
 * Hver vurderingsperiode har en definert [Periode] og [Vurdering], men trenger ikke å ha et grunnlag knyttet til seg.
 * I de fleste tilfeller er vurderingen gjort av en saksbehandler, men det finnes unntak, som [Formue] hvor systemet avgjør [Vurdering] basert på grunnlagene.
 */
sealed class Vurderingsperiode {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val vurdering: Vurdering
    abstract val grunnlag: Grunnlag?
    abstract val periode: Periode

    fun tilstøter(other: Vurderingsperiode): Boolean {
        return this.periode.tilstøter(other.periode)
    }

    abstract fun erLik(other: Vurderingsperiode): Boolean

    fun tilstøterOgErLik(other: Vurderingsperiode) = this.tilstøter(other) && this.erLik(other)
}
