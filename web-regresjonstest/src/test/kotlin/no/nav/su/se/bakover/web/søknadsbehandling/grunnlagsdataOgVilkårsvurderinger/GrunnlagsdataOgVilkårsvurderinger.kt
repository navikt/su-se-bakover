package no.nav.su.se.bakover.web.søknadsbehandling.grunnlagsdataOgVilkårsvurderinger

/**
 * Vil kun stemme dersom clock er før 2021-05-01 (da vil formuegrenser øke med et element)
 */
fun tomGrunnlagsdataOgVilkårsvurderingerResponse(): String {
    //language=JSON
    return """
    {
      "uføre":null,
      "lovligOpphold": null,
      "fradrag":[],
      "bosituasjon":[],
      "formue":{
        "vurderinger":[],
        "resultat": null,
        "formuegrenser":[        
          {
            "gyldigFra":"2020-05-01",
            "beløp":50676
          }
        ]
     },
      "utenlandsopphold":null,
      "opplysningsplikt":null,
      "pensjon":null,
      "familiegjenforening": null,
      "flyktning": null,
      "personligOppmøte": null
    }
    """.trimIndent()
}
