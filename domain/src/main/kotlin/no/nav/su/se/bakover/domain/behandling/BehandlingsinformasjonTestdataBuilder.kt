package no.nav.su.se.bakover.domain.behandling

import org.jetbrains.annotations.TestOnly

/**
 * Dette er kanskje ikke den beste plassen å legge ting som kun skal brukes i tester.
 * Se også SøknadInnholdTestdataBuilder
 */
@TestOnly
fun Behandlingsinformasjon.withAlleVilkårOppfylt() =
    Behandlingsinformasjon(
        uførhet = Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = uførhet?.uføregrad ?: 20,
            forventetInntekt = uførhet?.forventetInntekt ?: 10,
            begrunnelse = uførhet?.begrunnelse,
        ),
        flyktning = Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
            begrunnelse = flyktning?.begrunnelse
        ),
        lovligOpphold = Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
            begrunnelse = lovligOpphold?.begrunnelse
        ),
        fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = fastOppholdINorge?.begrunnelse
        ),
        institusjonsopphold = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
            begrunnelse = institusjonsopphold?.begrunnelse,
        ),
        oppholdIUtlandet = Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
            begrunnelse = oppholdIUtlandet?.begrunnelse
        ),
        formue = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            borSøkerMedEPS = formue?.borSøkerMedEPS ?: false,
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
            epsVerdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = formue?.epsVerdier?.verdiIkkePrimærbolig ?: 0,
                verdiEiendommer = formue?.epsVerdier?.verdiEiendommer ?: 0,
                verdiKjøretøy = formue?.epsVerdier?.verdiKjøretøy ?: 0,
                innskudd = formue?.epsVerdier?.innskudd ?: 0,
                verdipapir = formue?.epsVerdier?.verdipapir ?: 0,
                pengerSkyldt = formue?.epsVerdier?.pengerSkyldt ?: 0,
                kontanter = formue?.epsVerdier?.kontanter ?: 0,
                depositumskonto = formue?.epsVerdier?.depositumskonto ?: 0,
            ),
            begrunnelse = formue?.begrunnelse
        ),
        personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = personligOppmøte?.begrunnelse
        ),
        bosituasjon = Behandlingsinformasjon.Bosituasjon(
            epsFnr = null,
            delerBolig = bosituasjon?.delerBolig ?: false,
            ektemakeEllerSamboerUførFlyktning = bosituasjon?.ektemakeEllerSamboerUførFlyktning,
            begrunnelse = bosituasjon?.begrunnelse,
        ),
        ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle
    )

@TestOnly
fun Behandlingsinformasjon.withVilkårAvslått() =
    this.withAlleVilkårOppfylt().patch(
        Behandlingsinformasjon(
            uførhet = Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                uføregrad = null,
                forventetInntekt = null,
                begrunnelse = null
            )
        )
    )

@TestOnly
fun Behandlingsinformasjon.withVilkårIkkeVurdert() =
    Behandlingsinformasjon(
        uførhet = null,
        flyktning = null,
        lovligOpphold = null,
        fastOppholdINorge = null,
        institusjonsopphold = null,
        oppholdIUtlandet = null,
        formue = null,
        personligOppmøte = null,
        bosituasjon = null,
        ektefelle = null
    )

@TestOnly
fun extractBehandlingsinformasjon(behandling: Behandling) =
    behandling.behandlingsinformasjon()
