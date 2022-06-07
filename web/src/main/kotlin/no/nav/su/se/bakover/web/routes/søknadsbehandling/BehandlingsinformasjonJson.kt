package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon

internal data class BehandlingsinformasjonJson(
    val flyktning: FlyktningJson? = null,
    val lovligOpphold: LovligOppholdJson? = null,
    val fastOppholdINorge: FastOppholdINorgeJson? = null,
    val institusjonsopphold: InstitusjonsoppholdJson? = null,
    val personligOppmøte: PersonligOppmøteJson? = null,
) {
    companion object {
        internal fun Behandlingsinformasjon.toJson() =
            BehandlingsinformasjonJson(
                flyktning = flyktning?.toJson(),
                lovligOpphold = lovligOpphold?.toJson(),
                fastOppholdINorge = fastOppholdINorge?.toJson(),
                institusjonsopphold = institusjonsopphold?.toJson(),
                personligOppmøte = personligOppmøte?.toJson(),
            )
    }
}

internal fun behandlingsinformasjonFromJson(b: BehandlingsinformasjonJson) =
    Behandlingsinformasjon(
        flyktning = b.flyktning?.let { f ->
            Behandlingsinformasjon.Flyktning(
                status = Behandlingsinformasjon.Flyktning.Status.valueOf(f.status),
            )
        },
        lovligOpphold = b.lovligOpphold?.let { l ->
            Behandlingsinformasjon.LovligOpphold(
                status = Behandlingsinformasjon.LovligOpphold.Status.valueOf(l.status),
            )
        },
        fastOppholdINorge = b.fastOppholdINorge?.let { f ->
            Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.valueOf(f.status),
            )
        },
        institusjonsopphold = b.institusjonsopphold?.let { i ->
            Behandlingsinformasjon.Institusjonsopphold(
                status = Behandlingsinformasjon.Institusjonsopphold.Status.valueOf(i.status),
            )
        },
        personligOppmøte = b.personligOppmøte?.let { p ->
            Behandlingsinformasjon.PersonligOppmøte(
                status = Behandlingsinformasjon.PersonligOppmøte.Status.valueOf(p.status),
            )
        },
    )

internal fun Behandlingsinformasjon.Flyktning.toJson() = FlyktningJson(status = status.name)

internal fun Behandlingsinformasjon.LovligOpphold.toJson() = LovligOppholdJson(status = status.name)

internal fun Behandlingsinformasjon.FastOppholdINorge.toJson() = FastOppholdINorgeJson(status = status.name)

internal fun Behandlingsinformasjon.Institusjonsopphold.toJson() = InstitusjonsoppholdJson(status = status.name)

internal fun Behandlingsinformasjon.PersonligOppmøte.toJson() = PersonligOppmøteJson(status = status.name)

internal inline fun <reified T : Enum<T>> enumContains(s: String) = enumValues<T>().any { it.name == s }

internal data class FlyktningJson(val status: String)

internal data class LovligOppholdJson(val status: String)

internal data class FastOppholdINorgeJson(val status: String)

internal data class InstitusjonsoppholdJson(val status: String)

internal data class PersonligOppmøteJson(val status: String)
