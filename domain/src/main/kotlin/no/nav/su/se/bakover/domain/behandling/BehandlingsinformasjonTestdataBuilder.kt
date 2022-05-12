package no.nav.su.se.bakover.domain.behandling

import org.jetbrains.annotations.TestOnly

/**
 * Dette er kanskje ikke den beste plassen å legge ting som kun skal brukes i tester.
 * Se også SøknadInnholdTestdataBuilder
 */
@TestOnly
fun Behandlingsinformasjon.withAlleVilkårOppfylt() =
    Behandlingsinformasjon(
        flyktning = Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
        ),
        lovligOpphold = Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
        ),
        fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
        ),
        institusjonsopphold = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
            begrunnelse = institusjonsopphold?.begrunnelse,
        ),
        formue = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = formue?.verdier?.verdiIkkePrimærbolig ?: 0,
                verdiEiendommer = formue?.verdier?.verdiEiendommer ?: 0,
                verdiKjøretøy = formue?.verdier?.verdiKjøretøy ?: 0,
                innskudd = formue?.verdier?.innskudd ?: 0,
                verdipapir = formue?.verdier?.verdipapir ?: 0,
                pengerSkyldt = formue?.verdier?.pengerSkyldt ?: 0,
                kontanter = formue?.verdier?.kontanter ?: 0,
                depositumskonto = formue?.verdier?.depositumskonto ?: 0,
            ),
            epsVerdier = null,
            begrunnelse = formue?.begrunnelse,
        ),
        personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = personligOppmøte?.begrunnelse,
        ),
    )

@TestOnly
fun Behandlingsinformasjon.withAlleVilkårAvslått() =
    Behandlingsinformasjon(
        flyktning = Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt,
        ),
        lovligOpphold = Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårIkkeOppfylt,
        ),
        fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt,
        ),
        institusjonsopphold = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårIkkeOppfylt,
            begrunnelse = institusjonsopphold?.begrunnelse,
        ),
        formue = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = formue?.verdier?.verdiIkkePrimærbolig ?: 90000000,
                verdiEiendommer = formue?.verdier?.verdiEiendommer ?: 0,
                verdiKjøretøy = formue?.verdier?.verdiKjøretøy ?: 0,
                innskudd = formue?.verdier?.innskudd ?: 0,
                verdipapir = formue?.verdier?.verdipapir ?: 0,
                pengerSkyldt = formue?.verdier?.pengerSkyldt ?: 0,
                kontanter = formue?.verdier?.kontanter ?: 0,
                depositumskonto = formue?.verdier?.depositumskonto ?: 0,
            ),
            epsVerdier = null,
            begrunnelse = formue?.begrunnelse,
        ),
        personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
            begrunnelse = personligOppmøte?.begrunnelse,
        ),
    )

@TestOnly
fun Behandlingsinformasjon.withAvslåttFlyktning(): Behandlingsinformasjon {
    return withAlleVilkårOppfylt().patch(
        Behandlingsinformasjon(
            flyktning = Behandlingsinformasjon.Flyktning(
                status = Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt,
            ),
        ),
    )
}
