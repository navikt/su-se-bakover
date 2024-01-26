package no.nav.su.se.bakover.domain.brev

import vilkår.formue.domain.Formuegrunnlag
import vilkår.formue.domain.Verdier

data class FormueForBrev(
    val søkersFormue: FormueVerdierForBrev,
    val epsFormue: FormueVerdierForBrev?,
    val totalt: Int,
)

data class FormueVerdierForBrev(
    val verdiSekundærBoliger: Int,
    val verdiSekundærKjøretøyer: Int,
    val pengerIBanken: Int,
    val depositumskonto: Int,
    val pengerIKontanter: Int,
    val aksjerOgVerdiPapir: Int,
    val pengerSøkerSkyldes: Int,
)

fun Formuegrunnlag.tilFormueForBrev(): FormueForBrev {
    return FormueForBrev(
        søkersFormue = this.søkersFormue.tilFormueVerdierForBrev(),
        epsFormue = this.epsFormue?.tilFormueVerdierForBrev(),
        totalt = this.sumFormue(),
    )
}

fun Verdier.tilFormueVerdierForBrev(): FormueVerdierForBrev {
    return FormueVerdierForBrev(
        verdiSekundærBoliger = this.verdiEiendommer + this.verdiIkkePrimærbolig,
        verdiSekundærKjøretøyer = this.verdiKjøretøy,
        pengerIBanken = this.innskudd,
        depositumskonto = this.depositumskonto,
        pengerIKontanter = this.kontanter,
        aksjerOgVerdiPapir = this.verdipapir,
        pengerSøkerSkyldes = this.pengerSkyldt,
    )
}
