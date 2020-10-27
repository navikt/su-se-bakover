package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.EktefellePartnerSamboer
import no.nav.su.se.bakover.domain.beregning.Sats

data class BehandlingsinformasjonJson(
    val uførhet: UførhetJson? = null,
    val flyktning: FlyktningJson? = null,
    val lovligOpphold: LovligOppholdJson? = null,
    val fastOppholdINorge: FastOppholdINorgeJson? = null,
    val oppholdIUtlandet: OppholdIUtlandetJson? = null,
    val formue: FormueJson? = null,
    val personligOppmøte: PersonligOppmøteJson? = null,
    val bosituasjon: BosituasjonJson? = null,
    val utledetSats: Sats? = null,
    val ektefelle: EktefelleJson? = null
) {
    companion object {
        fun Behandlingsinformasjon.toJson() =
            BehandlingsinformasjonJson(
                uførhet = uførhet?.toJson(),
                flyktning = flyktning?.toJson(),
                lovligOpphold = lovligOpphold?.toJson(),
                fastOppholdINorge = fastOppholdINorge?.toJson(),
                oppholdIUtlandet = oppholdIUtlandet?.toJson(),
                formue = formue?.toJson(),
                personligOppmøte = personligOppmøte?.toJson(),
                bosituasjon = bosituasjon?.toJson(),
                utledetSats = bosituasjon?.utledSats(),
                ektefelle = ektefelle?.toJson(),
            )
    }
}

fun BehandlingsinformasjonJson.isValid() =
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
                forventetInntekt = u.forventetInntekt
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
        oppholdIUtlandet = b.oppholdIUtlandet?.let { o ->
            Behandlingsinformasjon.OppholdIUtlandet(
                status = Behandlingsinformasjon.OppholdIUtlandet.Status.valueOf(o.status),
                begrunnelse = o.begrunnelse
            )
        },
        formue = b.formue?.let { f ->
            Behandlingsinformasjon.Formue(
                status = Behandlingsinformasjon.Formue.Status.valueOf(f.status),
                verdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = f.verdier?.verdiIkkePrimærbolig,
                    verdiKjøretøy = f.verdier?.verdiKjøretøy,
                    innskudd = f.verdier?.innskudd,
                    verdipapir = f.verdier?.verdipapir,
                    pengerSkyldt = f.verdier?.pengerSkyldt,
                    kontanter = f.verdier?.kontanter,
                    depositumskonto = f.verdier?.depositumskonto
                ),
                ektefellesVerdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = f.ektefellesVerdier?.verdiIkkePrimærbolig,
                    verdiKjøretøy = f.ektefellesVerdier?.verdiKjøretøy,
                    innskudd = f.ektefellesVerdier?.innskudd,
                    verdipapir = f.ektefellesVerdier?.verdipapir,
                    pengerSkyldt = f.ektefellesVerdier?.pengerSkyldt,
                    kontanter = f.ektefellesVerdier?.kontanter,
                    depositumskonto = f.ektefellesVerdier?.depositumskonto
                ),
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
                delerBolig = s.delerBolig,
                delerBoligMed = s.delerBoligMed?.let { Boforhold.DelerBoligMed.valueOf(it) },
                ektemakeEllerSamboerUnder67År = s.ektemakeEllerSamboerUnder67År,
                ektemakeEllerSamboerUførFlyktning = s.ektemakeEllerSamboerUførFlyktning,
                begrunnelse = s.begrunnelse
            )
        },
        ektefelle = b.ektefelle?.let { e ->
            if (e.fnr != null) EktefellePartnerSamboer.Ektefelle(e.fnr) else EktefellePartnerSamboer.IngenEktefelle
        }
    )

internal fun Behandlingsinformasjon.Uførhet.toJson() =
    UførhetJson(
        status = status.name,
        uføregrad = uføregrad,
        forventetInntekt = forventetInntekt
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

internal fun Behandlingsinformasjon.OppholdIUtlandet.toJson() =
    OppholdIUtlandetJson(
        status = status.name,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.Formue.toJson() =
    FormueJson(
        status = status.name,
        verdier = this.verdier?.toJson(),
        ektefellesVerdier = this.ektefellesVerdier?.toJson(),
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.Formue.Verdier.toJson() =
    VerdierJson(
        verdiIkkePrimærbolig = verdiIkkePrimærbolig,
        verdiKjøretøy = verdiKjøretøy,
        innskudd = innskudd,
        verdipapir = verdipapir,
        pengerSkyldt = pengerSkyldt,
        kontanter = kontanter,
        depositumskonto = kontanter,
    )

internal fun Behandlingsinformasjon.PersonligOppmøte.toJson() =
    PersonligOppmøteJson(
        status = status.name,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.Bosituasjon.toJson() =
    BosituasjonJson(
        delerBolig = delerBolig,
        delerBoligMed = delerBoligMed?.name,
        ektemakeEllerSamboerUnder67År = ektemakeEllerSamboerUnder67År,
        ektemakeEllerSamboerUførFlyktning = ektemakeEllerSamboerUførFlyktning,
        begrunnelse = begrunnelse
    )

internal fun EktefellePartnerSamboer.toJson() = when (this) {
    is EktefellePartnerSamboer.Ektefelle -> EktefelleJson(fnr = this.fnr)
    is EktefellePartnerSamboer.IngenEktefelle -> EktefelleJson(fnr = null)
}

inline fun <reified T : Enum<T>> enumContains(s: String) = enumValues<T>().any { it.name == s }

internal fun UførhetJson.isValid() =
    enumContains<Behandlingsinformasjon.Uførhet.Status>(status)

internal fun FlyktningJson.isValid() =
    enumContains<Behandlingsinformasjon.Flyktning.Status>(status)

internal fun LovligOppholdJson.isValid() =
    enumContains<Behandlingsinformasjon.LovligOpphold.Status>(status)

internal fun BosituasjonJson.isValid() =
    delerBoligMed == null || enumContains<Boforhold.DelerBoligMed>(delerBoligMed)

internal fun PersonligOppmøteJson.isValid() =
    enumContains<Behandlingsinformasjon.PersonligOppmøte.Status>(status)

internal fun OppholdIUtlandetJson.isValid() =
    enumContains<Behandlingsinformasjon.OppholdIUtlandet.Status>(status)

internal fun FormueJson.isValid() =
    enumContains<Behandlingsinformasjon.Formue.Status>(status)

internal fun FastOppholdINorgeJson.isValid() =
    enumContains<Behandlingsinformasjon.FastOppholdINorge.Status>(status)

internal fun EktefelleJson.isValid() = true

data class UførhetJson(
    val status: String,
    val uføregrad: Int?,
    val forventetInntekt: Int?
)

data class FlyktningJson(
    val status: String,
    val begrunnelse: String?
)

data class LovligOppholdJson(
    val status: String,
    val begrunnelse: String?
)

data class FastOppholdINorgeJson(
    val status: String,
    val begrunnelse: String?
)

data class OppholdIUtlandetJson(
    val status: String,
    val begrunnelse: String?
)

data class FormueJson(
    val status: String,
    val verdier: VerdierJson?,
    val ektefellesVerdier: VerdierJson?,
    val begrunnelse: String?
)

data class VerdierJson(
    val verdiIkkePrimærbolig: Int?,
    val verdiKjøretøy: Int?,
    val innskudd: Int?,
    val verdipapir: Int?,
    val pengerSkyldt: Int?,
    val kontanter: Int?,
    val depositumskonto: Int?,
)

data class PersonligOppmøteJson(
    val status: String,
    val begrunnelse: String?
)

data class BosituasjonJson(
    val delerBolig: Boolean,
    val delerBoligMed: String?,
    val ektemakeEllerSamboerUnder67År: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
    val begrunnelse: String?
)

data class EktefelleJson(val fnr: Fnr?)
