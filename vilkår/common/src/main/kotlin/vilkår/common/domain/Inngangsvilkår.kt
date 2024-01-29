package vilkår.common.domain

/**
Et inngangsvilkår er de vilkårene som kan føre til avslag før det beregnes?
Her har vi utelatt for høy inntekt (SU<0) og su under minstegrense (SU<2%)
 */
sealed interface Inngangsvilkår {
    data object Uførhet : Inngangsvilkår
    data object Formue : Inngangsvilkår
    data object Flyktning : Inngangsvilkår
    data object LovligOpphold : Inngangsvilkår
    data object Institusjonsopphold : Inngangsvilkår
    data object Utenlandsopphold : Inngangsvilkår
    data object PersonligOppmøte : Inngangsvilkår
    data object FastOppholdINorge : Inngangsvilkår
    data object Opplysningsplikt : Inngangsvilkår
    data object Familiegjenforening : Inngangsvilkår
    data object Pensjon : Inngangsvilkår
}
