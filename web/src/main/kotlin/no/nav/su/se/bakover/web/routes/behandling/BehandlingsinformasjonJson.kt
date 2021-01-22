package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Sats
import java.time.LocalDate

internal data class BehandlingsinformasjonJson(
    val uførhet: UførhetJson? = null,
    val flyktning: FlyktningJson? = null,
    val lovligOpphold: LovligOppholdJson? = null,
    val fastOppholdINorge: FastOppholdINorgeJson? = null,
    val institusjonsopphold: InstitusjonsoppholdJson? = null,
    val oppholdIUtlandet: OppholdIUtlandetJson? = null,
    val formue: FormueJson? = null,
    val personligOppmøte: PersonligOppmøteJson? = null,
    val bosituasjon: BosituasjonJson? = null,
    val utledetSats: Sats? = null,
    val ektefelle: EktefelleJson? = null
) {
    companion object {
        internal fun Behandlingsinformasjon.toJson() =
            BehandlingsinformasjonJson(
                uførhet = uførhet?.toJson(),
                flyktning = flyktning?.toJson(),
                lovligOpphold = lovligOpphold?.toJson(),
                fastOppholdINorge = fastOppholdINorge?.toJson(),
                institusjonsopphold = institusjonsopphold?.toJson(),
                oppholdIUtlandet = oppholdIUtlandet?.toJson(),
                formue = formue?.toJson(),
                personligOppmøte = personligOppmøte?.toJson(),
                bosituasjon = bosituasjon?.toJson(),
                utledetSats = bosituasjon?.utledSats(),
                ektefelle = ektefelle?.toJson(),
            )
    }
}

internal fun BehandlingsinformasjonJson.isValid() =
    uførhet?.isValid() ?: true &&
        flyktning?.isValid() ?: true &&
        lovligOpphold?.isValid() ?: true &&
        fastOppholdINorge?.isValid() ?: true &&
        oppholdIUtlandet?.isValid() ?: true &&
        formue?.isValid() ?: true &&
        personligOppmøte?.isValid() ?: true &&
        bosituasjon?.isValid() ?: true &&
        ektefelle?.isValid() ?: true

internal fun behandlingsinformasjonFromJson(b: BehandlingsinformasjonJson) =
    Behandlingsinformasjon(
        uførhet = b.uførhet?.let { u ->
            Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.valueOf(u.status),
                uføregrad = u.uføregrad,
                forventetInntekt = u.forventetInntekt,
                begrunnelse = u.begrunnelse
            )
        },
        flyktning = b.flyktning?.let { f ->
            Behandlingsinformasjon.Flyktning(
                status = Behandlingsinformasjon.Flyktning.Status.valueOf(f.status),
                begrunnelse = f.begrunnelse
            )
        },
        lovligOpphold = b.lovligOpphold?.let { l ->
            Behandlingsinformasjon.LovligOpphold(
                status = Behandlingsinformasjon.LovligOpphold.Status.valueOf(l.status),
                begrunnelse = l.begrunnelse
            )
        },
        fastOppholdINorge = b.fastOppholdINorge?.let { f ->
            Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.valueOf(f.status),
                begrunnelse = f.begrunnelse
            )
        },
        institusjonsopphold = b.institusjonsopphold?.let { i ->
            Behandlingsinformasjon.Institusjonsopphold(
                status = Behandlingsinformasjon.Institusjonsopphold.Status.valueOf(i.status),
                begrunnelse = i.begrunnelse
            )
        },
        oppholdIUtlandet = b.oppholdIUtlandet?.let { o ->
            Behandlingsinformasjon.OppholdIUtlandet(
                status = Behandlingsinformasjon.OppholdIUtlandet.Status.valueOf(o.status),
                begrunnelse = o.begrunnelse
            )
        },
        formue = b.formue?.let { f ->
            Behandlingsinformasjon.Formue(
                status = Behandlingsinformasjon.Formue.Status.valueOf(f.status),
                borSøkerMedEPS = f.borSøkerMedEPS,
                verdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = f.verdier?.verdiIkkePrimærbolig,
                    verdiEiendommer = f.verdier?.verdiEiendommer,
                    verdiKjøretøy = f.verdier?.verdiKjøretøy,
                    innskudd = f.verdier?.innskudd,
                    verdipapir = f.verdier?.verdipapir,
                    pengerSkyldt = f.verdier?.pengerSkyldt,
                    kontanter = f.verdier?.kontanter,
                    depositumskonto = f.verdier?.depositumskonto
                ),
                epsVerdier = b.ektefelle?.fnr?.let {
                    Behandlingsinformasjon.Formue.Verdier(
                        verdiIkkePrimærbolig = f.epsVerdier?.verdiIkkePrimærbolig,
                        verdiEiendommer = f.epsVerdier?.verdiEiendommer,
                        verdiKjøretøy = f.epsVerdier?.verdiKjøretøy,
                        innskudd = f.epsVerdier?.innskudd,
                        verdipapir = f.epsVerdier?.verdipapir,
                        pengerSkyldt = f.epsVerdier?.pengerSkyldt,
                        kontanter = f.epsVerdier?.kontanter,
                        depositumskonto = f.epsVerdier?.depositumskonto
                    )
                },
                begrunnelse = f.begrunnelse
            )
        },
        personligOppmøte = b.personligOppmøte?.let { p ->
            Behandlingsinformasjon.PersonligOppmøte(
                status = Behandlingsinformasjon.PersonligOppmøte.Status.valueOf(p.status),
                begrunnelse = p.begrunnelse
            )
        },
        bosituasjon = b.bosituasjon?.let { s ->
            Behandlingsinformasjon.Bosituasjon(
                epsAlder = s.epsAlder,
                delerBolig = s.delerBolig,
                ektemakeEllerSamboerUførFlyktning = s.ektemakeEllerSamboerUførFlyktning,
                begrunnelse = s.begrunnelse
            )
        },
        ektefelle = b.ektefelle?.let { e ->
            if (e.fnr != null) {
                Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
                    fnr = e.fnr,
                    navn = e.navn,
                    kjønn = e.kjønn,
                    fødselsdato = e.fødselsdato,
                    adressebeskyttelse = e.adressebeskyttelse,
                    skjermet = e.skjermet,
                )
            } else Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle
        }
    )

internal fun Behandlingsinformasjon.Uførhet.toJson() =
    UførhetJson(
        status = status.name,
        uføregrad = uføregrad,
        forventetInntekt = forventetInntekt,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.Flyktning.toJson() =
    FlyktningJson(
        status = status.name,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.LovligOpphold.toJson() =
    LovligOppholdJson(
        status = status.name,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.FastOppholdINorge.toJson() =
    FastOppholdINorgeJson(
        status = status.name,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.Institusjonsopphold.toJson() =
    InstitusjonsoppholdJson(
        status = status.name,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.OppholdIUtlandet.toJson() =
    OppholdIUtlandetJson(
        status = status.name,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.Formue.toJson() =
    FormueJson(
        status = status.name,
        verdier = this.verdier?.toJson(),
        borSøkerMedEPS = this.borSøkerMedEPS,
        epsVerdier = this.epsVerdier?.toJson(),
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.Formue.Verdier.toJson() =
    VerdierJson(
        verdiIkkePrimærbolig = verdiIkkePrimærbolig,
        verdiEiendommer = verdiEiendommer,
        verdiKjøretøy = verdiKjøretøy,
        innskudd = innskudd,
        verdipapir = verdipapir,
        pengerSkyldt = pengerSkyldt,
        kontanter = kontanter,
        depositumskonto = depositumskonto,
    )

internal fun Behandlingsinformasjon.PersonligOppmøte.toJson() =
    PersonligOppmøteJson(
        status = status.name,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.Bosituasjon.toJson() =
    BosituasjonJson(
        epsAlder = epsAlder,
        delerBolig = delerBolig,
        ektemakeEllerSamboerUførFlyktning = ektemakeEllerSamboerUførFlyktning,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.EktefellePartnerSamboer.toJson() = when (this) {
    is Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle -> EktefelleJson(
        fnr = this.fnr,
        navn = this.navn,
        kjønn = this.kjønn,
        fødselsdato = this.fødselsdato,
        alder = this.getAlder(),
        adressebeskyttelse = this.adressebeskyttelse,
        skjermet = this.skjermet,
    )
    is Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle -> null
}

internal inline fun <reified T : Enum<T>> enumContains(s: String) = enumValues<T>().any { it.name == s }

internal fun UførhetJson.isValid() =
    enumContains<Behandlingsinformasjon.Uførhet.Status>(status)

internal fun FlyktningJson.isValid() =
    enumContains<Behandlingsinformasjon.Flyktning.Status>(status)

internal fun LovligOppholdJson.isValid() =
    enumContains<Behandlingsinformasjon.LovligOpphold.Status>(status)

internal fun BosituasjonJson.isValid() = epsAlder != null || delerBolig != null

internal fun PersonligOppmøteJson.isValid() =
    enumContains<Behandlingsinformasjon.PersonligOppmøte.Status>(status)

internal fun OppholdIUtlandetJson.isValid() =
    enumContains<Behandlingsinformasjon.OppholdIUtlandet.Status>(status)

internal fun FormueJson.isValid() =
    enumContains<Behandlingsinformasjon.Formue.Status>(status)

internal fun FastOppholdINorgeJson.isValid() =
    enumContains<Behandlingsinformasjon.FastOppholdINorge.Status>(status)

internal fun EktefelleJson.isValid() = true

internal data class UførhetJson(
    val status: String,
    val uføregrad: Int?,
    val forventetInntekt: Int?,
    val begrunnelse: String?,
)

internal data class FlyktningJson(
    val status: String,
    val begrunnelse: String?
)

internal data class LovligOppholdJson(
    val status: String,
    val begrunnelse: String?
)

internal data class FastOppholdINorgeJson(
    val status: String,
    val begrunnelse: String?
)

internal data class InstitusjonsoppholdJson(
    val status: String,
    val begrunnelse: String?
)

internal data class OppholdIUtlandetJson(
    val status: String,
    val begrunnelse: String?
)

internal data class FormueJson(
    val status: String,
    val verdier: VerdierJson?,
    val borSøkerMedEPS: Boolean,
    val epsVerdier: VerdierJson?,
    val begrunnelse: String?
)

internal data class VerdierJson(
    val verdiIkkePrimærbolig: Int?,
    val verdiEiendommer: Int?,
    val verdiKjøretøy: Int?,
    val innskudd: Int?,
    val verdipapir: Int?,
    val pengerSkyldt: Int?,
    val kontanter: Int?,
    val depositumskonto: Int?,
)

internal data class PersonligOppmøteJson(
    val status: String,
    val begrunnelse: String?
)

internal data class BosituasjonJson(
    val epsAlder: Int?,
    val delerBolig: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
    val begrunnelse: String?
)

internal data class EktefelleJson(
    val fnr: Fnr?,
    val navn: Person.Navn?,
    val kjønn: String?,
    val fødselsdato: LocalDate?,
    val adressebeskyttelse: String?,
    val skjermet: Boolean?,
    val alder: Int?
)
