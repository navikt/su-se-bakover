package vilkår.vurderinger.domain

import arrow.core.Either
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.Vilkår
import vilkår.common.domain.Vurdering
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.formue.domain.FormueVilkår
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår

interface Vilkårsvurderinger {
    val vilkår: Set<Vilkår>

    val lovligOpphold: LovligOppholdVilkår
    val formue: FormueVilkår
    val utenlandsopphold: UtenlandsoppholdVilkår
    val opplysningsplikt: OpplysningspliktVilkår
    val fastOpphold: FastOppholdINorgeVilkår
    val personligOppmøte: PersonligOppmøteVilkår
    val institusjonsopphold: InstitusjonsoppholdVilkår

    val erVurdert: Boolean get() = vilkår.none { it.vurdering is Vurdering.Uavklart }
    val avslagsgrunner: List<Avslagsgrunn> get() = vilkår.flatMap { it.avslagsgrunner }

    fun uføreVilkår(): Either<VilkårEksistererIkke, UføreVilkår>
    fun uføreVilkårKastHvisAlder(): UføreVilkår
    fun fastOppholdVilkår(): FastOppholdINorgeVilkår = fastOpphold
    fun lovligOppholdVilkår(): LovligOppholdVilkår = lovligOpphold
    fun institusjonsoppholdVilkår(): InstitusjonsoppholdVilkår = institusjonsopphold
    fun utenlandsoppholdVilkår(): UtenlandsoppholdVilkår = utenlandsopphold
    fun formueVilkår(): FormueVilkår = formue
    fun personligOppmøteVilkår(): PersonligOppmøteVilkår = personligOppmøte
    fun opplysningspliktVilkår(): OpplysningspliktVilkår = opplysningsplikt

    fun erInnvilget(): Boolean = vilkår.all { it.erInnvilget }
    fun erAvslag(): Boolean = vilkår.any { it.erAvslag }

    fun resultat(): Vurdering = when {
        erInnvilget() -> Vurdering.Innvilget
        erAvslag() -> Vurdering.Avslag
        else -> Vurdering.Uavklart
    }

    val periode: Periode?
        get() {
            return vilkår.flatMap { vilkår ->
                when (vilkår) {
                    is UføreVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }
                    is PensjonsVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }
                    is FlyktningVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }
                    is FamiliegjenforeningVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }
                    is FastOppholdINorgeVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }
                    is LovligOppholdVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }
                    is InstitusjonsoppholdVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }
                    is UtenlandsoppholdVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }
                    is FormueVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }
                    is PersonligOppmøteVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }
                    is OpplysningspliktVilkår.Vurdert -> vilkår.vurderingsperioder.map { it.periode }

                    FastOppholdINorgeVilkår.IkkeVurdert,
                    FlyktningVilkår.IkkeVurdert,
                    FormueVilkår.IkkeVurdert,
                    LovligOppholdVilkår.IkkeVurdert,
                    InstitusjonsoppholdVilkår.IkkeVurdert,
                    OpplysningspliktVilkår.IkkeVurdert,
                    PersonligOppmøteVilkår.IkkeVurdert,
                    UføreVilkår.IkkeVurdert,
                    UtenlandsoppholdVilkår.IkkeVurdert,
                    PensjonsVilkår.IkkeVurdert,
                    FamiliegjenforeningVilkår.IkkeVurdert,
                    -> emptyList()

                    else -> throw IllegalStateException("Feil ved mapping av vilkår $this to periode")
                }
            }.ifNotEmpty { this.minAndMaxOf() }
        }

    fun lagTidslinje(periode: Periode): Vilkårsvurderinger

    fun erLik(other: Vilkårsvurderinger): Boolean

    fun copyWithNewIds(): Vilkårsvurderinger
}

data object VilkårEksistererIkke

/**
 * Skal kun kalles fra undertyper av [Vilkårsvurderinger].
 */
fun Vilkårsvurderinger.kastHvisPerioderErUlike() {
    // Merk at hvert enkelt [Vilkår] passer på sine egne data (som f.eks. at periodene er sorterte og uten duplikater)
    vilkår.map { Pair(it.vilkår, it.perioder) }.zipWithNext { a, b ->
        // Vilkår med tomme perioder har ikke blitt vurdert enda.
        if (a.second.isNotEmpty() && b.second.isNotEmpty()) {
            require(a.second == b.second) {
                "Periodene til Vilkårsvurderinger er ulike. $a vs $b."
            }
        }
    }
}
