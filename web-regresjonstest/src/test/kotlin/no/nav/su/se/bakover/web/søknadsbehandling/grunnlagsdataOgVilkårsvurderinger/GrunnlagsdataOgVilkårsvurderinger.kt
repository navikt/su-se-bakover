package no.nav.su.se.bakover.web.søknadsbehandling.grunnlagsdataOgVilkårsvurderinger

fun tomGrunnlagsdataOgVilkårsvurderingerResponse(): String {
    //language=JSON
    return """
    {
      "uføre":null,
      "fradrag":[],
      "bosituasjon":[],
      "formue":{
        "vurderinger":[],
        "resultat":"MåInnhenteMerInformasjon",
        "formuegrenser":[        
          {
            "gyldigFra":"2020-05-01",
            "beløp":50676
          }
        ]
     },
      "utenlandsopphold":null,
      "opplysningsplikt":null
    }
    """.trimIndent()
}
