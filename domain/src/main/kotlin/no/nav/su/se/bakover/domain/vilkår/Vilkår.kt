package no.nav.su.se.bakover.domain.vilkår

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.erSortert
import no.nav.su.se.bakover.common.periode.harDuplikater
import java.time.LocalDate

/**
 * Et [Vilkår], dersom det er vurdert, er delt opp i 1 eller flere [Vurderingsperiode].
 * Hver enkelt [Vurderingsperiode] har en definert [Periode] og [Vurdering], mens [Vilkår] har ikke disse entydige grensene:
 * - [no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling]: Et vilkår for en stønadsperiode må ha et entydig resultat og en sammenhengende periode.
 * - [no.nav.su.se.bakover.domain.revurdering.Revurdering] og [no.nav.su.se.bakover.domain.regulering.Regulering]: Kan gå på tvers av stønadsperioder og kan da bestå av flere enn et resultat og kan ha hull i periodene.
 * Revurdering/Regulering kan ha strengere regler enn dette i sine respektive implementasjoner.
 */
sealed class Vilkår {
    abstract val vurdering: Vurdering
    abstract val erAvslag: Boolean
    abstract val erInnvilget: Boolean
    abstract val vilkår: Inngangsvilkår

    /**
     * Vurderte vilkår vil ha en eller flere [Periode], mens ikke-vurderte vilkår vil ikke ha en [Periode].
     * Periodene vil være sortert og vil ikke ha duplikater.
     * De skal også være slått sammen, der det er mulig.
     * Obs: Periodene kan fremdeles ha hull.
     */
    abstract val perioder: List<Periode>

    abstract fun hentTidligesteDatoForAvslag(): LocalDate?

    abstract fun erLik(other: Vilkår): Boolean
    abstract fun lagTidslinje(periode: Periode): Vilkår
    abstract fun slåSammenLikePerioder(): Vilkår

    protected fun kastHvisPerioderErUsortertEllerHarDuplikater() {
        require(perioder.erSortert())
        require(!perioder.harDuplikater())
        // TODO jah: Vurder å legg på require(perioder.minsteAntallSammenhengendePerioder() == perioder)
    }
}
