package no.nav.su.se.bakover.web.søknadsbehandling.behandlingsinformasjon

fun tomBehandlingsinformasjonResponse(): String {
    return """
      {
        "flyktning":null,
        "lovligOpphold":null,
        "fastOppholdINorge":null,
        "institusjonsopphold":null,
        "formue":null,
        "personligOppmøte":null
      }
    """.trimIndent()
}
