package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.Behandling

/**
 * Dette er kanskje ikke den beste plassen å legge ting som kun skal brukes i tester.
 * Se også SøknadInnholdTestdataBuilder
 */

fun Behandlingsinformasjon.withAlleVilkårOppfylt() =
    Behandlingsinformasjon(
        uførhet = Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = uførhet?.uføregrad ?: 20,
            forventetInntekt = uførhet?.forventetInntekt ?: 10
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
        oppholdIUtlandet = Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
            begrunnelse = oppholdIUtlandet?.begrunnelse
        ),
        formue = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdiIkkePrimærbolig = formue?.verdiIkkePrimærbolig ?: 0,
            verdiKjøretøy = formue?.verdiKjøretøy ?: 0,
            innskudd = formue?.innskudd ?: 0,
            verdipapir = formue?.verdipapir ?: 0,
            pengerSkyldt = formue?.pengerSkyldt ?: 0,
            kontanter = formue?.kontanter ?: 0,
            depositumskonto = formue?.depositumskonto ?: 0,
            begrunnelse = formue?.begrunnelse
        ),
        personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = personligOppmøte?.begrunnelse
        ),
        bosituasjon = Behandlingsinformasjon.Bosituasjon(
            delerBolig = bosituasjon?.delerBolig ?: false,
            delerBoligMed = bosituasjon?.delerBoligMed,
            ektemakeEllerSamboerUnder67År = bosituasjon?.ektemakeEllerSamboerUnder67År,
            ektemakeEllerSamboerUførFlyktning = bosituasjon?.ektemakeEllerSamboerUførFlyktning,
            begrunnelse = bosituasjon?.begrunnelse
        ),
        ektefelle = Behandlingsinformasjon.Ektefelle(harEktefellePartnerSamboer = false, fnr = null)
    )

fun Behandlingsinformasjon.withVilkårAvslått() =
    this.withAlleVilkårOppfylt().patch(
        Behandlingsinformasjon(
            uførhet = Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                uføregrad = null,
                forventetInntekt = null
            )
        )
    )

fun Behandlingsinformasjon.withVilkårIkkeVurdert() =
    Behandlingsinformasjon(
        uførhet = null,
        flyktning = null,
        lovligOpphold = null,
        fastOppholdINorge = null,
        oppholdIUtlandet = null,
        formue = null,
        personligOppmøte = null,
        bosituasjon = null,
        ektefelle = null
    )

fun extractBehandlingsinformasjon(behandling: Behandling) =
    behandling.behandlingsinformasjon()
