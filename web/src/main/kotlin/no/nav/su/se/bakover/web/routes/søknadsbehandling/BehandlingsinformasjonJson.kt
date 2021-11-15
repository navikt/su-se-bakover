package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Sats

internal data class BehandlingsinformasjonJson(
    val uførhet: UførhetJson? = null,
    val flyktning: FlyktningJson? = null,
    val lovligOpphold: LovligOppholdJson? = null,
    val fastOppholdINorge: FastOppholdINorgeJson? = null,
    val institusjonsopphold: InstitusjonsoppholdJson? = null,
    val formue: FormueJson? = null,
    val personligOppmøte: PersonligOppmøteJson? = null,
    val utledetSats: Sats? = null,
) {
    companion object {
        internal fun Behandlingsinformasjon.toJson(borSøkerMedEPS: Boolean?, sats: Sats?) =
            BehandlingsinformasjonJson(
                uførhet = uførhet?.toJson(),
                flyktning = flyktning?.toJson(),
                lovligOpphold = lovligOpphold?.toJson(),
                fastOppholdINorge = fastOppholdINorge?.toJson(),
                institusjonsopphold = institusjonsopphold?.toJson(),
                formue = formue?.toJson(borSøkerMedEPS!!), // borSøkerMedEps må eksistere før formue
                personligOppmøte = personligOppmøte?.toJson(),
                utledetSats = sats,
            )
    }
}

internal fun behandlingsinformasjonFromJson(b: BehandlingsinformasjonJson) =
    Behandlingsinformasjon(
        uførhet = b.uførhet?.let { u ->
            Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.valueOf(u.status),
                uføregrad = u.uføregrad,
                forventetInntekt = u.forventetInntekt,
                begrunnelse = u.begrunnelse,
            )
        },
        flyktning = b.flyktning?.let { f ->
            Behandlingsinformasjon.Flyktning(
                status = Behandlingsinformasjon.Flyktning.Status.valueOf(f.status),
                begrunnelse = f.begrunnelse,
            )
        },
        lovligOpphold = b.lovligOpphold?.let { l ->
            Behandlingsinformasjon.LovligOpphold(
                status = Behandlingsinformasjon.LovligOpphold.Status.valueOf(l.status),
                begrunnelse = l.begrunnelse,
            )
        },
        fastOppholdINorge = b.fastOppholdINorge?.let { f ->
            Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.valueOf(f.status),
                begrunnelse = f.begrunnelse,
            )
        },
        institusjonsopphold = b.institusjonsopphold?.let { i ->
            Behandlingsinformasjon.Institusjonsopphold(
                status = Behandlingsinformasjon.Institusjonsopphold.Status.valueOf(i.status),
                begrunnelse = i.begrunnelse,
            )
        },
        formue = b.formue?.let { f ->
            Behandlingsinformasjon.Formue(
                status = Behandlingsinformasjon.Formue.Status.valueOf(f.status),
                verdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = f.verdier?.verdiIkkePrimærbolig,
                    verdiEiendommer = f.verdier?.verdiEiendommer,
                    verdiKjøretøy = f.verdier?.verdiKjøretøy,
                    innskudd = f.verdier?.innskudd,
                    verdipapir = f.verdier?.verdipapir,
                    pengerSkyldt = f.verdier?.pengerSkyldt,
                    kontanter = f.verdier?.kontanter,
                    depositumskonto = f.verdier?.depositumskonto,
                ),
                epsVerdier = f.epsVerdier?.let {
                    Behandlingsinformasjon.Formue.Verdier(
                        verdiIkkePrimærbolig = it.verdiIkkePrimærbolig,
                        verdiEiendommer = it.verdiEiendommer,
                        verdiKjøretøy = it.verdiKjøretøy,
                        innskudd = it.innskudd,
                        verdipapir = it.verdipapir,
                        pengerSkyldt = it.pengerSkyldt,
                        kontanter = it.kontanter,
                        depositumskonto = it.depositumskonto,
                    )
                },
                begrunnelse = f.begrunnelse,
            )
        },
        personligOppmøte = b.personligOppmøte?.let { p ->
            Behandlingsinformasjon.PersonligOppmøte(
                status = Behandlingsinformasjon.PersonligOppmøte.Status.valueOf(p.status),
                begrunnelse = p.begrunnelse,
            )
        },
    )

internal fun Behandlingsinformasjon.Uførhet.toJson() =
    UførhetJson(
        status = status.name,
        uføregrad = uføregrad,
        forventetInntekt = forventetInntekt,
        begrunnelse = begrunnelse,
    )

internal fun Behandlingsinformasjon.Flyktning.toJson() =
    FlyktningJson(
        status = status.name,
        begrunnelse = begrunnelse,
    )

internal fun Behandlingsinformasjon.LovligOpphold.toJson() =
    LovligOppholdJson(
        status = status.name,
        begrunnelse = begrunnelse,
    )

internal fun Behandlingsinformasjon.FastOppholdINorge.toJson() =
    FastOppholdINorgeJson(
        status = status.name,
        begrunnelse = begrunnelse,
    )

internal fun Behandlingsinformasjon.Institusjonsopphold.toJson() =
    InstitusjonsoppholdJson(
        status = status.name,
        begrunnelse = begrunnelse,
    )

internal fun Behandlingsinformasjon.Formue.toJson(borSøkerMedEPS: Boolean) =
    FormueJson(
        status = status.name,
        verdier = this.verdier?.toJson(),
        borSøkerMedEPS = borSøkerMedEPS,
        epsVerdier = this.epsVerdier?.toJson(),
        begrunnelse = begrunnelse,
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
        begrunnelse = begrunnelse,
    )

internal inline fun <reified T : Enum<T>> enumContains(s: String) = enumValues<T>().any { it.name == s }

internal data class UførhetJson(
    val status: String,
    val uføregrad: Int?,
    val forventetInntekt: Int?,
    val begrunnelse: String?,
)

internal data class FlyktningJson(
    val status: String,
    val begrunnelse: String?,
)

internal data class LovligOppholdJson(
    val status: String,
    val begrunnelse: String?,
)

internal data class FastOppholdINorgeJson(
    val status: String,
    val begrunnelse: String?,
)

internal data class InstitusjonsoppholdJson(
    val status: String,
    val begrunnelse: String?,
)

internal data class FormueJson(
    val status: String,
    val verdier: VerdierJson?,
    val borSøkerMedEPS: Boolean,
    val epsVerdier: VerdierJson?,
    val begrunnelse: String?,
) {
    fun harVerdierOgErGyldig(): Boolean = verdier?.let {
        if (borSøkerMedEPS) {
            return verdier.depositumErMindreEllerLikInnskudd() && (epsVerdier?.depositumErMindreEllerLikInnskudd() ?: false)
        }

        return verdier.depositumErMindreEllerLikInnskudd()
    } ?: false
}

internal data class VerdierJson(
    val verdiIkkePrimærbolig: Int?,
    val verdiEiendommer: Int?,
    val verdiKjøretøy: Int?,
    val innskudd: Int?,
    val verdipapir: Int?,
    val pengerSkyldt: Int?,
    val kontanter: Int?,
    val depositumskonto: Int?,
) {
    fun depositumErMindreEllerLikInnskudd() = if (depositumskonto != null && innskudd != null) depositumskonto <= innskudd else false
}

internal data class PersonligOppmøteJson(
    val status: String,
    val begrunnelse: String?,
)
