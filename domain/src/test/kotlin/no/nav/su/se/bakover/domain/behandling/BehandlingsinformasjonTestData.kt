package no.nav.su.se.bakover.domain.behandling

object BehandlingsinformasjonTestData {

    val behandlingsinformasjonMedAlleVilkårOppfylt = Behandlingsinformasjon(
        flyktning = Flyktning.Oppfylt,
        lovligOpphold = LovligOpphold.Oppfylt,
        fastOppholdINorge = FastOppholdINorge.Oppfylt,
        institusjonsopphold = Institusjonsopphold.Oppfylt,
        formue = Formue.OppfyltMedEPS,
        personligOppmøte = PersonligOppmøte.Oppfylt,
    )

    object Flyktning {
        val Oppfylt = Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
        )
        val Uavklart = Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.Uavklart,
        )
    }

    object LovligOpphold {
        val Oppfylt = Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
        )
        val Uavklart = Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.Uavklart,
        )
    }

    object FastOppholdINorge {
        val Oppfylt = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = "det stemmer"
        )
        val Uavklart = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.Uavklart,
            begrunnelse = "det stemmer"
        )
    }

    object Institusjonsopphold {
        val Oppfylt = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
            begrunnelse = "det stemmer"
        )
        val Uavklart = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.Uavklart,
            begrunnelse = "det stemmer"
        )
    }

    object Formue {
        val OppfyltMedEPS = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 0,
                verdiEiendommer = 0,
                verdiKjøretøy = 12000,
                innskudd = 0,
                verdipapir = 0,
                pengerSkyldt = 0,
                kontanter = 1500,
                depositumskonto = 0
            ),
            epsVerdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 74500,
                verdiEiendommer = 0,
                verdiKjøretøy = 0,
                innskudd = 13000,
                verdipapir = 2500,
                pengerSkyldt = 0,
                kontanter = 0,
                depositumskonto = 0,
            ),
            begrunnelse = "ok"
        )
        val OppfyltUtenEPS = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 0,
                verdiEiendommer = 0,
                verdiKjøretøy = 12000,
                innskudd = 0,
                verdipapir = 0,
                pengerSkyldt = 0,
                kontanter = 1500,
                depositumskonto = 0
            ),
            epsVerdier = null,
            begrunnelse = "ok",
        )
        val Uavklart = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.MåInnhenteMerInformasjon,
            verdier = Behandlingsinformasjon.Formue.Verdier.lagTomVerdier(),
            epsVerdier = null,
            begrunnelse = null
        )
    }

    object PersonligOppmøte {
        val Oppfylt = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = "det stemmer"
        )
        val Uavklart = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.Uavklart,
            begrunnelse = "det stemmer"
        )
    }
}
