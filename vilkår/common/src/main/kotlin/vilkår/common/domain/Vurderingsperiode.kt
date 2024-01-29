package vilkår.common.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

/**
 * Et [Vilkår], dersom det er vurdert, er delt opp i 1 eller flere [Vurderingsperiode].
 * Hver vurderingsperiode har en definert [Periode] og [Vurdering], men trenger ikke å ha et grunnlag knyttet til seg.
 * I de fleste tilfeller er vurderingen gjort av en saksbehandler, men det finnes unntak, som [FormueVilkår] hvor systemet avgjør [Vurdering] basert på grunnlagene.
 */
interface Vurderingsperiode {
    val id: UUID
    val opprettet: Tidspunkt
    val vurdering: Vurdering
    val grunnlag: Grunnlag?
    val periode: Periode

    fun tilstøter(other: Vurderingsperiode): Boolean {
        return this.periode.tilstøter(other.periode)
    }

    /**
     * Denne skal ignorere id, opprettet og periode, men bør sjekke vurdering og periode + custom fields.
     * Brukes til å determinere om vi kan slå sammen like vurderingsperioder.
     */
    fun erLik(other: Vurderingsperiode): Boolean

    fun tilstøterOgErLik(other: Vurderingsperiode) = this.tilstøter(other) && this.erLik(other)
}
