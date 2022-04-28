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
            "gyldigFra":"2022-05-01",
            "beløp":53450
          },
          {
            "gyldigFra":"2021-05-01",
            "beløp":53200
          },
          {
            "gyldigFra":"2020-05-01",
            "beløp":50676
          }
        ]
     },
      "utenlandsopphold":null
    }
    """.trimIndent()
}
