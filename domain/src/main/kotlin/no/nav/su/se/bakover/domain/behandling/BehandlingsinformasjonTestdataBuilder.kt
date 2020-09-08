package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.Behandling

/**
 * Dette er kanskje ikke den beste plassen å legge ting som kun skal brukes i tester.
 * Se også SøknadInnholdTestdataBuilder
 */

fun Behandlingsinformasjon.withAlleVilkårOppfylt() =
    this.toDto().let {
        Behandlingsinformasjon(
            uførhet = Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                uføregrad = it.uførhet?.uføregrad ?: 20,
                forventetInntekt = it.uførhet?.forventetInntekt ?: 10
            ),
            flyktning = Behandlingsinformasjon.Flyktning(
                status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
                begrunnelse = it.flyktning?.begrunnelse
            ),
            lovligOpphold = Behandlingsinformasjon.LovligOpphold(
                status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
                begrunnelse = it.lovligOpphold?.begrunnelse
            ),
            fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
                begrunnelse = it.fastOppholdINorge?.begrunnelse
            ),
            oppholdIUtlandet = Behandlingsinformasjon.OppholdIUtlandet(
                status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
                begrunnelse = it.oppholdIUtlandet?.begrunnelse
            ),
            formue = Behandlingsinformasjon.Formue(
                status = Behandlingsinformasjon.Formue.Status.Ok,
                verdiIkkePrimærbolig = it.formue?.verdiIkkePrimærbolig ?: 0,
                verdiKjøretøy = it.formue?.verdiKjøretøy ?: 0,
                innskudd = it.formue?.innskudd ?: 0,
                verdipapir = it.formue?.verdipapir ?: 0,
                pengerSkyldt = it.formue?.pengerSkyldt ?: 0,
                kontanter = it.formue?.kontanter ?: 0,
                depositumskonto = it.formue?.depositumskonto ?: 0
            ),
            personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
                status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
                begrunnelse = it.personligOppmøte?.begrunnelse
            ),
            sats = Behandlingsinformasjon.Sats(
                delerBolig = it.sats?.delerBolig ?: false,
                delerBoligMed = it.sats?.delerBoligMed,
                ektemakeEllerSamboerUnder67År = it.sats?.ektemakeEllerSamboerUnder67År,
                ektemakeEllerSamboerUførFlyktning = it.sats?.ektemakeEllerSamboerUførFlyktning,
                begrunnelse = it.sats?.begrunnelse
            )
        )
    }

fun Behandlingsinformasjon.withVilkårAvslått() =
    this.toDto().let {
        Behandlingsinformasjon(
            uførhet = Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                uføregrad = null,
                forventetInntekt = null
            ),
            flyktning = Behandlingsinformasjon.Flyktning(
                status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
                begrunnelse = it.flyktning?.begrunnelse
            ),
            lovligOpphold = Behandlingsinformasjon.LovligOpphold(
                status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
                begrunnelse = it.lovligOpphold?.begrunnelse
            ),
            fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
                begrunnelse = it.fastOppholdINorge?.begrunnelse
            ),
            oppholdIUtlandet = Behandlingsinformasjon.OppholdIUtlandet(
                status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
                begrunnelse = it.oppholdIUtlandet?.begrunnelse
            ),
            formue = Behandlingsinformasjon.Formue(
                status = Behandlingsinformasjon.Formue.Status.Ok,
                verdiIkkePrimærbolig = it.formue?.verdiIkkePrimærbolig,
                verdiKjøretøy = it.formue?.verdiKjøretøy,
                innskudd = it.formue?.innskudd,
                verdipapir = it.formue?.verdipapir,
                pengerSkyldt = it.formue?.pengerSkyldt,
                kontanter = it.formue?.kontanter,
                depositumskonto = it.formue?.depositumskonto
            ),
            personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
                status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
                begrunnelse = it.personligOppmøte?.begrunnelse
            ),
            sats = Behandlingsinformasjon.Sats(
                delerBolig = it.sats?.delerBolig ?: false,
                delerBoligMed = it.sats?.delerBoligMed,
                ektemakeEllerSamboerUnder67År = it.sats?.ektemakeEllerSamboerUnder67År,
                ektemakeEllerSamboerUførFlyktning = it.sats?.ektemakeEllerSamboerUførFlyktning,
                begrunnelse = it.sats?.begrunnelse
            )
        )
    }

fun Behandlingsinformasjon.withVilkårIkkeVurdert() =
    Behandlingsinformasjon(
        uførhet = null,
        flyktning = null,
        lovligOpphold = null,
        fastOppholdINorge = null,
        oppholdIUtlandet = null,
        formue = null,
        personligOppmøte = null,
        sats = null
    )

fun extractBehandlingsinformasjon(behandling: Behandling) =
    behandling.toDto().behandlingsinformasjon.let {
        Behandlingsinformasjon(
            uførhet = it.uførhet?.let { u ->
                Behandlingsinformasjon.Uførhet(
                    status = u.status,
                    uføregrad = u.uføregrad,
                    forventetInntekt = u.forventetInntekt
                )
            },
            flyktning = it.flyktning?.let { f ->
                Behandlingsinformasjon.Flyktning(
                    status = f.status,
                    begrunnelse = f.begrunnelse
                )
            },
            lovligOpphold = it.lovligOpphold?.let { l ->
                Behandlingsinformasjon.LovligOpphold(
                    status = l.status,
                    begrunnelse = l.begrunnelse
                )
            },
            fastOppholdINorge = it.fastOppholdINorge?.let { f ->
                Behandlingsinformasjon.FastOppholdINorge(
                    status = f.status,
                    begrunnelse = f.begrunnelse
                )
            },
            oppholdIUtlandet = it.oppholdIUtlandet?.let { o ->
                Behandlingsinformasjon.OppholdIUtlandet(
                    status = o.status,
                    begrunnelse = o.begrunnelse
                )
            },
            formue = it.formue?.let { f ->
                Behandlingsinformasjon.Formue(
                    status = f.status,
                    verdiIkkePrimærbolig = f.verdiIkkePrimærbolig,
                    verdiKjøretøy = f.verdiKjøretøy,
                    innskudd = f.innskudd,
                    verdipapir = f.verdipapir,
                    pengerSkyldt = f.pengerSkyldt,
                    kontanter = f.kontanter,
                    depositumskonto = f.depositumskonto
                )
            },
            personligOppmøte = it.personligOppmøte?.let { p ->
                Behandlingsinformasjon.PersonligOppmøte(
                    status = p.status,
                    begrunnelse = p.begrunnelse
                )
            },
            sats = it.sats?.let { s ->
                Behandlingsinformasjon.Sats(
                    delerBolig = s.delerBolig,
                    delerBoligMed = s.delerBoligMed,
                    ektemakeEllerSamboerUnder67År = s.ektemakeEllerSamboerUnder67År,
                    ektemakeEllerSamboerUførFlyktning = s.ektemakeEllerSamboerUførFlyktning,
                    begrunnelse = s.begrunnelse
                )
            }
        )
    }
