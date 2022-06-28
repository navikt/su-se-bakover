package no.nav.su.se.bakover.domain.vilkår

/**
Et inngangsvilkår er de vilkårene som kan føre til avslag før det beregnes?
Her har vi utelatt for høy inntekt (SU<0) og su under minstegrense (SU<2%)
 */
sealed class Inngangsvilkår {
    object Uførhet : Inngangsvilkår()
    object Formue : Inngangsvilkår()
    object Flyktning : Inngangsvilkår()
    object LovligOpphold : Inngangsvilkår()
    object Institusjonsopphold : Inngangsvilkår()
    object Utenlandsopphold : Inngangsvilkår()
    object PersonligOppmøte : Inngangsvilkår()
    object FastOppholdINorge : Inngangsvilkår()
    object Opplysningsplikt : Inngangsvilkår()
    object Familiegjenforening : Inngangsvilkår()
    object Pensjon : Inngangsvilkår()
}
