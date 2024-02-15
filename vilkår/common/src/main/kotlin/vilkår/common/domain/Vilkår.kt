package vilkår.common.domain

import no.nav.su.se.bakover.common.tid.periode.Periode
import java.time.LocalDate

/**
 * Et [Vilkår], dersom det er vurdert, er delt opp i 1 eller flere [Vurderingsperiode].
 * Hver enkelt [Vurderingsperiode] har en definert [Periode] og [Vurdering], mens [Vilkår] har ikke disse entydige grensene:
 * - [no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling]: Et vilkår for en stønadsperiode må ha et entydig resultat og en sammenhengende periode.
 * - [no.nav.su.se.bakover.domain.revurdering.Revurdering] og [no.nav.su.se.bakover.domain.regulering.Regulering]: Kan gå på tvers av stønadsperioder og kan da bestå av flere enn et resultat og kan ha hull i periodene.
 * Revurdering/Regulering kan ha strengere regler enn dette i sine respektive implementasjoner.
 */
interface Vilkår {
    val vurdering: Vurdering
    val erAvslag: Boolean
    val erInnvilget: Boolean
    val vilkår: Inngangsvilkår
    val avslagsgrunner: List<Avslagsgrunn>

    /**
     * Vurderte vilkår vil ha en eller flere [Periode], mens ikke-vurderte vilkår vil ikke ha en [Periode].
     * Periodene vil være sortert og vil ikke ha duplikater.
     * De skal også være slått sammen, der det er mulig.
     * Obs: Periodene kan fremdeles ha hull.
     */
    val perioder: List<Periode>

    fun hentTidligesteDatoForAvslag(): LocalDate?

    fun erLik(other: Vilkår): Boolean
    fun lagTidslinje(periode: Periode): Vilkår
    fun slåSammenLikePerioder(): Vilkår
    fun copyWithNewId(): Vilkår
}
