package no.nav.su.se.bakover.web.søknadsbehandling.behandlingsinformasjon

fun tomBehandlingsinformasjonResponse(): String {
    return """
      {
        "institusjonsopphold":null,
        "personligOppmøte":null
      }
    """.trimIndent()
}
