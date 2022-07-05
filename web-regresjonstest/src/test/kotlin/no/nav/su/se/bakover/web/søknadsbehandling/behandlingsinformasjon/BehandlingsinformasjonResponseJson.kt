package no.nav.su.se.bakover.web.søknadsbehandling.behandlingsinformasjon

fun tomBehandlingsinformasjonResponse(): String {
    return """
      {
        "flyktning":null,
        "fastOppholdINorge":null,
        "institusjonsopphold":null
      }
    """.trimIndent()
}
