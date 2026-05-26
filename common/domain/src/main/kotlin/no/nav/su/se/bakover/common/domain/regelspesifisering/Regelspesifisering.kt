package no.nav.su.se.bakover.common.domain.regelspesifisering

/**
 Confluence: https://confluence.adeo.no/spaces/TESUS/pages/780350280/Regel+i+kode
 og https://confluence.adeo.no/spaces/TESUS/pages/720910102/Regelspesifisering+-+lov+i+kode

 Automatiske beregninger skal persisteres med anvendte regler og grunnlag som skal være regelspesifisert på Confluence (se link).
 Beregninger skal utvide interface [RegelspesifisertBeregning] og populere den med anvendt regel og grunnlag.

 [RegelspesifisertBeregning.benyttetRegel] populeres ved å bruke [Regelspesifiseringer.benyttRegelspesifisering]
 og [RegelspesifisertGrunnlag.benyttGrunnlag] hvor grunnlag beregningen er avhengig av legges til som parameter.
 Beregninger er som oftest avhengige av andre beregninger som grunnlag, så både [Regelspesifisering.Beregning] og
 [Regelspesifisering.Grunnlag] vil kunne være grunnlag/avhengigRegel for en beregning.

 Alle beregninger skal ha en test som verifiserer at et komplett regeltre blir riktig her: [beregning.domain.BeregningRegelspesifiseringTest]
**/
enum class Regelspesifiseringer(
    val kode: String,
    val versjon: String,
) {
    // Beregning
    REGEL_FRADRAG_EPS_OVER_FRIBELØP("REGEL-FRADRAG-EPS-OVER-FRIBELØP", "2"),
    REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET("REGEL-FRADRAG-MINUS-MINST-ARBEID-OG-FORVENTET", "2"),
    REGEL_SAMLET_FRADRAG("REGEL-SAMLET-FRADRAG", "2"),
    REGEL_BEREGN_SATS_ALDER_MÅNED("REGEL-BEREGN-SATS-ALDER-MÅNED", "2"),
    REGEL_BEREGN_SATS_UFØRE_MÅNED("REGEL-BEREGN-SATS-UFØRE-MÅNED", "2"),
    REGEL_SATS_MINUS_FRADRAG_AVRUNDET("REGEL-SATS-MINUS-FRADRAG-AVRUNDET", "2"),
    REGEL_SOSIALSTØNAD_UNDER_2_PROSENT("REGEL-SOSIALSTØNAD-UNDER-2-PROSENT", "2"),
    REGEL_MINDRE_ENN_2_PROSENT("REGEL-MINDRE-ENN-2-PROSENT", "2"),
    REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE("REGEL-TO-PROSENT-AV-HØY-SATS-UFØRE", "2"),
    REGEL_TO_PROSENT_AV_HØY_SATS_ALDER("REGEL-TO-PROSENT-AV-HØY-SATS-ALDER", "2"),
    REGEL_MÅNEDSBEREGNING("REGEL-MÅNEDSBEREGNING", "2"),

    // Inntektsgrunnlag
    REGEL_FRADRAG_MED_UFØRE("REGEL-FRADRAG-MED-UFØRE", "2"),

    // Vilkårsvurdering
    REGEL_FORMUE_HALV_G("REGEL-FORMUE-HALV-G", "2"),

    // Regulering
    REGEL_BEREGN_SATS_AAP_MÅNED("REGEL-BEREGN-SATS-AAP-MÅNED", "2"),
    ;

    fun benyttRegelspesifisering(
        verdi: String,
        avhengigeRegler: List<Regelspesifisering> = emptyList(),
    ) = Regelspesifisering.Beregning(
        kode = this.kode,
        versjon = this.versjon,
        avhengigeRegler = avhengigeRegler,
        verdi = verdi,
    )
}

enum class RegelspesifisertGrunnlag(
    val kode: String,
    val versjon: String,
) {
    GRUNNLAG_BOSITUASJON("GRUNNLAG-BOSITUASJON", "2"),
    GRUNNLAG_FRADRAG("GRUNNLAG-FRADRAG", "2"),
    GRUNNLAG_GRUNNBELØP("GRUNNLAG-GRUNNBELØP", "2"),
    GRUNNLAG_UFØRE_FAKTOR_ORDINÆR("GRUNNLAG-UFØRE-FAKTOR-ORDINÆR", "2"),
    GRUNNLAG_UFØRE_FAKTOR_HØY("GRUNNLAG-UFØRE-FAKTOR-HØY", "2"),
    GRUNNLAG_GARANTIPENSJON_ORDINÆR("GRUNNLAG-GARANTPIPENSJON-ORDINÆR", "2"),
    GRUNNLAG_GARANTIPENSJON_HØY("GRUNNLAG-GARANTPIPENSJON-HØY", "2"),
    GRUNNLAG_FORMUE("GRUNNLAG-FORMUE", "2"),
    GRUNNLAG_FORMUEGRENSE("GRUNNLAG-FORMUEGRENSE", "2"),
    GRUNNLAG_DAGSATS_AAP("GRUNNLAG-DAGSATS-AAP", "2"),

    // GRUNNLAG-INNTEKT_ETTER-UFØRE
    GRUNNLAG_UFØRETRYGD("GRUNNLAG-UFØRETRYGD", "2"),
    ;

    fun benyttGrunnlag(
        verdi: String,
        kilde: String = when (this) {
            GRUNNLAG_BOSITUASJON,
            GRUNNLAG_FRADRAG,
            GRUNNLAG_UFØRETRYGD,
            GRUNNLAG_FORMUE,
            -> "Saksbehandler"

            GRUNNLAG_UFØRE_FAKTOR_ORDINÆR,
            GRUNNLAG_UFØRE_FAKTOR_HØY,
            GRUNNLAG_GARANTIPENSJON_ORDINÆR,
            GRUNNLAG_GARANTIPENSJON_HØY,
            GRUNNLAG_GRUNNBELØP,
            GRUNNLAG_FORMUEGRENSE,
            GRUNNLAG_DAGSATS_AAP,
            -> "SU-App"
        },
    ) = Regelspesifisering.Grunnlag(
        kode,
        versjon,
        verdi,
        kilde,
    )
}

interface RegelspesifisertBeregning {
    val benyttetRegel: Regelspesifisering
}

sealed class Regelspesifisering {

    data class Beregning(
        val kode: String,
        val versjon: String,
        val verdi: String,
        val avhengigeRegler: List<Regelspesifisering>,
    ) : Regelspesifisering()

    data class Grunnlag(
        val kode: String,
        val versjon: String,
        val verdi: String,
        val kilde: String,
    ) : Regelspesifisering()

    data object BeregnetUtenSpesifisering : Regelspesifisering()
}
