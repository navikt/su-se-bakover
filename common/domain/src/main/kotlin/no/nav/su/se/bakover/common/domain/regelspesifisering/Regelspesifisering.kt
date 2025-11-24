package no.nav.su.se.bakover.common.domain.regelspesifisering

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock

enum class Regelspesifiseringer(
    val kode: String,
    val versjon: String,
) {
    REGEL_FRADRAG_EPS_OVER_FRIBELØP("REGEL-FRADRAG-EPS-OVER-FRIBELØP", "1"),
    REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET("REGEL-FRADRAG-MINUS-MINST-ARBEID-OG-FORVENTET", "1"),
    REGEL_SAMLET_FRADRAG("REGEL-SAMLET-FRADRAG", "1"),
    REGEL_BEREGN_SATS_ALDER_MÅNED("REGEL-BEREGN-SATS-ALDER-MÅNED", "1"),
    REGEL_BEREGN_SATS_UFØRE_MÅNED("REGEL-BEREGN-SATS-UFØRE-MÅNED", "1"),
    REGEL_MÅNEDSBEREGNING("REGEL-MÅNEDSBEREGNING", "1"),
    REGEL_SOSIALSTØNAD_UNDER_2_PROSENT("REGEL-SOSIALSTØNAD-UNDER-2-PROSENT", "1"),
    REGEL_MINDRE_ENN_2_PROSENT("REGEL-MINDRE-ENN-2-PROSENT", "1"),
    REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE("REGEL-TO-PROSENT-AV-HØY-SATS-UFØRE", "1"),
    REGEL_TO_PROSENT_AV_HØY_SATS_ALDER("REGEL-TO-PROSENT-AV-HØY-SATS-ALDER", "1"),
    ;

    fun benyttRegelspesifisering(
        avhengigeRegler: List<Regelspesifisering> = emptyList(),
    ) = Regelspesifisering.Beregning(
        kode = this.kode,
        versjon = this.versjon,
        benyttetTidspunkt = Tidspunkt.now(Clock.systemUTC()),
        avhengigeRegler = avhengigeRegler,
    )
}

enum class RegelspesifisertGrunnlag(
    val kode: String,
    val versjon: String,
) {
    GRUNNLAG_BOTILSTAND("GRUNNLAG-BOTILSTAND", "1"),
    GRUNNLAG_FRADRAG("GRUNNLAG-FRADRAG", "1"),
    GRUNNLAG_UFØRE_FAKTOR_ORDINÆR("GRUNNLAG-UFØRE-FAKTOR-ORDINÆR", "1"),
    GRUNNLAG_UFØRE_FAKTOR_HØY("GRUNNLAG-UFØRE-FAKTOR-HØY", "1"),
    GRUNNLAG_GARANTPIPENSJON_ORDINÆR("GRUNNLAG-GARANTPIPENSJON-ORDINÆR", "1"),
    GRUNNLAG_GARANTPIPENSJON_HØY("GRUNNLAG-GARANTPIPENSJON-HØY", "1"),
    GRUNNLAG_GRUNNBELØP("GRUNNLAG-GRUNNBELØP", "1"),
    ;

    fun benyttGrunnlag(
        kilde: String? = when (this) {
            GRUNNLAG_BOTILSTAND,
            GRUNNLAG_FRADRAG,
            -> null // TODO
            GRUNNLAG_UFØRE_FAKTOR_ORDINÆR,
            GRUNNLAG_UFØRE_FAKTOR_HØY,
            GRUNNLAG_GARANTPIPENSJON_ORDINÆR,
            GRUNNLAG_GARANTPIPENSJON_HØY,
            GRUNNLAG_GRUNNBELØP,
            -> "SU-App"
        },
    ) = Regelspesifisering.Grunnlag(
        kode,
        versjon,
        Tidspunkt.now(Clock.systemUTC()),
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
        val benyttetTidspunkt: Tidspunkt,
        val avhengigeRegler: List<Regelspesifisering>,
    ) : Regelspesifisering()

    data class Grunnlag(
        val kode: String,
        val versjon: String,
        val benyttetTidspunkt: Tidspunkt,
        val kilde: String? = null, // TODO bjg må settes
    ) : Regelspesifisering()

    data object BeregnetUtenSpesifisering : Regelspesifisering()
}
