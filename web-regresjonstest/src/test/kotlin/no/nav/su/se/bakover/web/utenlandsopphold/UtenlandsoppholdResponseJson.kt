package no.nav.su.se.bakover.web.utenlandsopphold

data class UtenlandsResponseJsonData(
    val versjon: Long = 2,
    val fraOgMed: String = "2021-05-05",
    val tilOgMed: String = "2021-10-10",
    val journalpostIder: String = "[\"1234567\"]",
    val dokumentasjon: String = "Sannsynliggjort",
    val opprettetAv: String = "Z990Lokal",
    val endretAv: String = "Z990Lokal",
    val antallDagerForPeriode: Long = 157,
    val erAnnullert: Boolean = false,
)

fun utenlandsoppholdResponseJson(
    antallDagerTotal: Long = 157,
    elements: List<UtenlandsResponseJsonData> = emptyList(),
) = /* language=JSON */ """
{
  "utenlandsopphold":[
    ${
elements.joinToString {
    """
    {
      "periode":{
        "fraOgMed":"${it.fraOgMed}",
        "tilOgMed":"${it.tilOgMed}"
      },
      "journalposter":${it.journalpostIder},
      "dokumentasjon":"${it.dokumentasjon}",
      "opprettetAv":"${it.opprettetAv}",
      "opprettetTidspunkt":"2021-01-01T01:02:03.456789Z",
      "endretAv":"${it.endretAv}",
      "endretTidspunkt":"2021-01-01T01:02:03.456789Z",
      "versjon":${it.versjon},
      "antallDager":${it.antallDagerForPeriode},
      "erAnnullert":${it.erAnnullert}
    }
    """.trimIndent()
}
}
  ],
  "antallDager":$antallDagerTotal
}
""".trimIndent()
