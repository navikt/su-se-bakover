package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon

internal data class BehandlingsinformasjonJson(
    val institusjonsopphold: InstitusjonsoppholdJson? = null,
) {
    companion object {
        internal fun Behandlingsinformasjon.toJson() =
            BehandlingsinformasjonJson(
                institusjonsopphold = institusjonsopphold?.toJson(),
            )
    }
}

internal fun behandlingsinformasjonFromJson(b: BehandlingsinformasjonJson) =
    Behandlingsinformasjon(
        institusjonsopphold = b.institusjonsopphold?.let { i ->
            Behandlingsinformasjon.Institusjonsopphold(
                status = Behandlingsinformasjon.Institusjonsopphold.Status.valueOf(i.status),
            )
        },
    )

internal fun Behandlingsinformasjon.Institusjonsopphold.toJson() = InstitusjonsoppholdJson(status = status.name)

internal inline fun <reified T : Enum<T>> enumContains(s: String) = enumValues<T>().any { it.name == s }

internal data class FastOppholdINorgeJson(val status: String)

internal data class InstitusjonsoppholdJson(val status: String)
