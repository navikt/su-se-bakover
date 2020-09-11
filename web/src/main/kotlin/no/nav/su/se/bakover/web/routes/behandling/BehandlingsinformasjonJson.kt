package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Sats

data class BehandlingsinformasjonJson(
    val uførhet: UførhetJson? = null,
    val flyktning: FlyktningJson? = null,
    val lovligOpphold: LovligOppholdJson? = null,
    val fastOppholdINorge: FastOppholdINorgeJson? = null,
    val oppholdIUtlandet: OppholdIUtlandetJson? = null,
    val formue: FormueJson? = null,
    val personligOppmøte: PersonligOppmøteJson? = null,
    val sats: SatsJson? = null,
    val utledetSats: Sats? = null
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
                sats = sats?.toJson(),
                utledetSats = sats?.utledSats()
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
        sats?.isValid() ?: true

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
                verdiIkkePrimærbolig = f.verdiIkkePrimærbolig,
                verdiKjøretøy = f.verdiKjøretøy,
                innskudd = f.innskudd,
                verdipapir = f.verdipapir,
                pengerSkyldt = f.pengerSkyldt,
                kontanter = f.kontanter,
                depositumskonto = f.depositumskonto,
                begrunnelse = f.begrunnelse
            )
        },
        personligOppmøte = b.personligOppmøte?.let { p ->
            Behandlingsinformasjon.PersonligOppmøte(
                status = Behandlingsinformasjon.PersonligOppmøte.Status.valueOf(p.status),
                begrunnelse = p.begrunnelse
            )
        },
        sats = b.sats?.let { s ->
            Behandlingsinformasjon.Sats(
                delerBolig = s.delerBolig,
                delerBoligMed = s.delerBoligMed?.let { Boforhold.DelerBoligMed.valueOf(it) },
                ektemakeEllerSamboerUnder67År = s.ektemakeEllerSamboerUnder67År,
                ektemakeEllerSamboerUførFlyktning = s.ektemakeEllerSamboerUførFlyktning,
                begrunnelse = s.begrunnelse
            )
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
        verdiIkkePrimærbolig = verdiIkkePrimærbolig,
        verdiKjøretøy = verdiKjøretøy,
        innskudd = innskudd,
        verdipapir = verdipapir,
        pengerSkyldt = pengerSkyldt,
        kontanter = kontanter,
        depositumskonto = depositumskonto,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.PersonligOppmøte.toJson() =
    PersonligOppmøteJson(
        status = status.name,
        begrunnelse = begrunnelse
    )

internal fun Behandlingsinformasjon.Sats.toJson() =
    SatsJson(
        delerBolig = delerBolig,
        delerBoligMed = delerBoligMed?.name,
        ektemakeEllerSamboerUnder67År = ektemakeEllerSamboerUnder67År,
        ektemakeEllerSamboerUførFlyktning = ektemakeEllerSamboerUførFlyktning,
        begrunnelse = begrunnelse
    )

inline fun <reified T : Enum<T>> enumContains(s: String) = enumValues<T>().any { it.name == s }

internal fun UførhetJson.isValid() =
    enumContains<Behandlingsinformasjon.Uførhet.Status>(status)
internal fun FlyktningJson.isValid() =
    enumContains<Behandlingsinformasjon.Flyktning.Status>(status)
internal fun LovligOppholdJson.isValid() =
    enumContains<Behandlingsinformasjon.LovligOpphold.Status>(status)
internal fun SatsJson.isValid() =
    delerBoligMed == null || enumContains<Boforhold.DelerBoligMed>(delerBoligMed)
internal fun PersonligOppmøteJson.isValid() =
    enumContains<Behandlingsinformasjon.PersonligOppmøte.Status>(status)
internal fun OppholdIUtlandetJson.isValid() =
    enumContains<Behandlingsinformasjon.OppholdIUtlandet.Status>(status)
internal fun FormueJson.isValid() =
    enumContains<Behandlingsinformasjon.Formue.Status>(status)
internal fun FastOppholdINorgeJson.isValid() =
    enumContains<Behandlingsinformasjon.FastOppholdINorge.Status>(status)

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
    val verdiIkkePrimærbolig: Int?,
    val verdiKjøretøy: Int?,
    val innskudd: Int?,
    val verdipapir: Int?,
    val pengerSkyldt: Int?,
    val kontanter: Int?,
    val depositumskonto: Int?,
    val begrunnelse: String?
)

data class PersonligOppmøteJson(
    val status: String,
    val begrunnelse: String?
)

data class SatsJson(
    val delerBolig: Boolean,
    val delerBoligMed: String?,
    val ektemakeEllerSamboerUnder67År: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
    val begrunnelse: String?
)
