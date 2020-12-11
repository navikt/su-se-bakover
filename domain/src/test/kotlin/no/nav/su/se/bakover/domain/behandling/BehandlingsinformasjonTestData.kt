package no.nav.su.se.bakover.domain.behandling

object BehandlingsinformasjonTestData {

    val behandlingsinformasjonMedAlleVilkårOppfylt = Behandlingsinformasjon(
        uførhet = Uførhet.Oppfylt,
        flyktning = Flyktning.Oppfylt,
        lovligOpphold = LovligOpphold.Oppfylt,
        fastOppholdINorge = FastOppholdINorge.Oppfylt,
        institusjonsopphold = Institusjonsopphold.Oppfylt,
        oppholdIUtlandet = OppholdIUtlandet.Oppfylt,
        formue = Formue.Oppfylt,
        personligOppmøte = PersonligOppmøte.Oppfylt,
        bosituasjon = Bosituasjon.Oppfylt,
        ektefelle = EktefellePartnerSamboer.Oppfylt
    )

    object Uførhet {
        val Oppfylt = Behandlingsinformasjon.Uførhet(
            Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = 100,
            forventetInntekt = 5000,
            begrunnelse = null
        )
        val IkkeOppfylt = Behandlingsinformasjon.Uførhet(
            Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
            uføregrad = null,
            forventetInntekt = null,
            begrunnelse = null
        )
        val Uavklart = Behandlingsinformasjon.Uførhet(
            Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
            uføregrad = 1,
            forventetInntekt = 1,
            begrunnelse = null
        )
    }

    object Flyktning {
        val Oppfylt = Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
            begrunnelse = "det stemmer"
        )
        val IkkeOppfylt = Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt,
            begrunnelse = "det stemmer"
        )
        val Uavklart = Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.Uavklart,
            begrunnelse = "det stemmer"
        )
    }

    object LovligOpphold {
        val Oppfylt = Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
            begrunnelse = "det stemmer"
        )
        val IkkeOppfylt = Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårIkkeOppfylt,
            begrunnelse = "det stemmer"
        )
        val Uavklart = Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.Uavklart,
            begrunnelse = "det stemmer"
        )
    }

    object FastOppholdINorge {
        val Oppfylt = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = "det stemmer"
        )
        val IkkeOppfylt = Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt,
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
        val IkkeOppfylt = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårIkkeOppfylt,
            begrunnelse = "det stemmer"
        )
        val Uavklart = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.Uavklart,
            begrunnelse = "det stemmer"
        )
    }

    object OppholdIUtlandet {
        val Oppfylt = Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
            begrunnelse = "det stemmer"
        )
        val IkkeOppfylt = Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet,
            begrunnelse = "det stemmer"
        )
        val Uavklart = Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.Uavklart,
            begrunnelse = "det stemmer"
        )
    }

    object Formue {
        val Oppfylt = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            borSøkerMedEPS = true,
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
        val IkkeOppfylt = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt,
            borSøkerMedEPS = true,
            verdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 999999999,
                verdiEiendommer = 50000,
                verdiKjøretøy = 12000,
                innskudd = 0,
                verdipapir = 0,
                pengerSkyldt = 0,
                kontanter = 1500,
                depositumskonto = 0
            ),
            epsVerdier = Behandlingsinformasjon.Formue.Verdier(
                verdiIkkePrimærbolig = 74500,
                verdiEiendommer = 50000,
                verdiKjøretøy = 0,
                innskudd = 13000,
                verdipapir = 2500,
                pengerSkyldt = 0,
                kontanter = 0,
                depositumskonto = 0,
            ),
            begrunnelse = "ok"
        )
        val Uavklart = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.MåInnhenteMerInformasjon,
            verdier = null,
            borSøkerMedEPS = false,
            epsVerdier = null,
            begrunnelse = null
        )
    }

    object PersonligOppmøte {
        val Oppfylt = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = "det stemmer"
        )
        val IkkeOppfylt = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
            begrunnelse = "det stemmer"
        )
        val Uavklart = Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.Uavklart,
            begrunnelse = "det stemmer"
        )
    }

    object Bosituasjon {
        val Oppfylt = Behandlingsinformasjon.Bosituasjon(
            epsFnr = null,
            delerBolig = false,
            begrunnelse = "det stemmer",
            ektemakeEllerSamboerUførFlyktning = null,
        )
    }

    object EktefellePartnerSamboer {
        val Oppfylt = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle
    }
}
