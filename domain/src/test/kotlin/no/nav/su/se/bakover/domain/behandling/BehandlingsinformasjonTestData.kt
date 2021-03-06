package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.Fnr
import java.time.LocalDate

object BehandlingsinformasjonTestData {

    val behandlingsinformasjonMedAlleVilkårOppfylt = Behandlingsinformasjon(
        uførhet = Uførhet.Oppfylt,
        flyktning = Flyktning.Oppfylt,
        lovligOpphold = LovligOpphold.Oppfylt,
        fastOppholdINorge = FastOppholdINorge.Oppfylt,
        institusjonsopphold = Institusjonsopphold.Oppfylt,
        oppholdIUtlandet = OppholdIUtlandet.Oppfylt,
        formue = Formue.OppfyltMedEPS,
        personligOppmøte = PersonligOppmøte.Oppfylt,
        bosituasjon = Bosituasjon.OppfyltDelerIkkeBolig,
        ektefelle = EktefellePartnerSamboer.OppfyltIngenEPS
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
            begrunnelse = "ok"
        )
        val IkkeOppfylt = Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt,
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
        val OppfyltDelerBolig = Behandlingsinformasjon.Bosituasjon(
            ektefelle = EktefellePartnerSamboer.OppfyltIngenEPS,
            delerBolig = true,
            begrunnelse = "det stemmer",
            ektemakeEllerSamboerUførFlyktning = null,
        )
        val OppfyltDelerIkkeBolig = Behandlingsinformasjon.Bosituasjon(
            ektefelle = EktefellePartnerSamboer.OppfyltIngenEPS,
            delerBolig = false,
            begrunnelse = "det stemmer",
            ektemakeEllerSamboerUførFlyktning = null,
        )
        val IkkeOppfylltDelerBoligIkkeUtfyllt = Behandlingsinformasjon.Bosituasjon(
            ektefelle = EktefellePartnerSamboer.OppfyltIngenEPS,
            delerBolig = null,
            begrunnelse = "det stemmer",
            ektemakeEllerSamboerUførFlyktning = true,
        )
        val OppfyltEPSUførFlyktning = Behandlingsinformasjon.Bosituasjon(
            ektefelle = EktefellePartnerSamboer.OppyltEPSUnder67,
            delerBolig = null,
            begrunnelse = "det stemmer",
            ektemakeEllerSamboerUførFlyktning = true,
        )
        val OppfyltEPSIkkeUførFlyktning = Behandlingsinformasjon.Bosituasjon(
            ektefelle = EktefellePartnerSamboer.OppyltEPSUnder67,
            delerBolig = null,
            begrunnelse = "det stemmer",
            ektemakeEllerSamboerUførFlyktning = false,
        )
        val OppfyltEPSUførFlyktningIkkeUtfyllt = Behandlingsinformasjon.Bosituasjon(
            ektefelle = EktefellePartnerSamboer.OppyltEPSOverEllerLik67,
            delerBolig = null,
            begrunnelse = "det stemmer",
            ektemakeEllerSamboerUførFlyktning = null,
        )
        val IkkeOppfylltBeggeVerdierNull = Behandlingsinformasjon.Bosituasjon(
            ektefelle = EktefellePartnerSamboer.OppfyltIngenEPS,
            delerBolig = null,
            begrunnelse = "det stemmer",
            ektemakeEllerSamboerUførFlyktning = null,
        )
    }

    object EktefellePartnerSamboer {
        val OppfyltIngenEPS = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle
        val OppyltEPSOverEllerLik67 = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
            fnr = Fnr(fnr = "12345678901"),
            navn = null,
            kjønn = null,
            fødselsdato = LocalDate.of(1900, 1, 1),
            adressebeskyttelse = null,
            skjermet = null
        )
        val OppyltEPSUnder67 = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
            fnr = Fnr(fnr = "12345678901"),
            navn = null,
            kjønn = null,
            fødselsdato = LocalDate.now(),
            adressebeskyttelse = null,
            skjermet = null
        )
    }
}
