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
    REGEL_SATS_MINUS_FRADRAG_AVRUNDET("REGEL-SATS-MINUS-FRADRAG-AVRUNDET", "1"),
    REGEL_SOSIALSTØNAD_UNDER_2_PROSENT("REGEL-SOSIALSTØNAD-UNDER-2-PROSENT", "1"),
    REGEL_MINDRE_ENN_2_PROSENT("REGEL-MINDRE-ENN-2-PROSENT", "1"),
    REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE("REGEL-TO-PROSENT-AV-HØY-SATS-UFØRE", "1"),
    REGEL_TO_PROSENT_AV_HØY_SATS_ALDER("REGEL-TO-PROSENT-AV-HØY-SATS-ALDER", "1"),
    REGEL_MÅNEDSBEREGNING("REGEL-MÅNEDSBEREGNING", "1"),
    ;

    fun benyttRegelspesifisering(
        verdi: String,
        avhengigeRegler: List<Regelspesifisering> = emptyList(),
    ) = Regelspesifisering.Beregning(
        kode = this.kode,
        versjon = this.versjon,
        benyttetTidspunkt = Tidspunkt.now(Clock.systemUTC()),
        avhengigeRegler = avhengigeRegler,
        verdi = verdi,
    )
}

enum class RegelspesifisertGrunnlag(
    val kode: String,
    val versjon: String,
) {
    GRUNNLAG_BOSITUASJON("GRUNNLAG-BOSITUASJON", "1"),
    GRUNNLAG_FRADRAG("GRUNNLAG-FRADRAG", "1"),
    GRUNNLAG_GRUNNBELØP("GRUNNLAG-GRUNNBELØP", "1"),
    GRUNNLAG_UFØRE_FAKTOR_ORDINÆR("GRUNNLAG-UFØRE-FAKTOR-ORDINÆR", "1"),
    GRUNNLAG_UFØRE_FAKTOR_HØY("GRUNNLAG-UFØRE-FAKTOR-HØY", "1"),
    GRUNNLAG_GARANTIPENSJON_ORDINÆR("GRUNNLAG-GARANTPIPENSJON-ORDINÆR", "1"),
    GRUNNLAG_GARANTIPENSJON_HØY("GRUNNLAG-GARANTPIPENSJON-HØY", "1"),
    ;

    fun benyttGrunnlag(
        verdi: String,
        kilde: String = when (this) {
            GRUNNLAG_BOSITUASJON,
            GRUNNLAG_FRADRAG,
            -> "Saksbehandler"

            GRUNNLAG_UFØRE_FAKTOR_ORDINÆR,
            GRUNNLAG_UFØRE_FAKTOR_HØY,
            GRUNNLAG_GARANTIPENSJON_ORDINÆR,
            GRUNNLAG_GARANTIPENSJON_HØY,
            GRUNNLAG_GRUNNBELØP,
            -> "SU-App"
        },
    ) = Regelspesifisering.Grunnlag(
        kode,
        versjon,
        Tidspunkt.now(Clock.systemUTC()),
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
        val benyttetTidspunkt: Tidspunkt,
        val verdi: String,
        val avhengigeRegler: List<Regelspesifisering>,
    ) : Regelspesifisering()

    data class Grunnlag(
        val kode: String,
        val versjon: String,
        val benyttetTidspunkt: Tidspunkt,
        val verdi: String,
        val kilde: String,
    ) : Regelspesifisering()

    data object BeregnetUtenSpesifisering : Regelspesifisering()
}
