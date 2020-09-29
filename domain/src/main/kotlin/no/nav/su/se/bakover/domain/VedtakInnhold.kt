package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.beregning.Fradrag

data class VedtakInnhold(
    val dato: String,
    val fødselsnummer: Fnr,
    val fornavn: String,
    val etternavn: String,
    val adresse: String?,
    val husnummer: String?,
    val bruksenhet: String?,
    val postnummer: String?,
    val poststed: String?,
    val månedsbeløp: Int?,
    val fradato: String?,
    val tildato: String?,
    val sats: String?,
    val satsbeløp: Int?,
    val satsGrunn: String,
    val redusertStønadStatus: Boolean,
    val redusertStønadGrunn: String?,
    val fradrag: List<Fradrag>,
    val fradragSum: Int,
    val status: Behandling.BehandlingsStatus,
    val avslagsgrunn: Avslagsgrunn?,
    val avslagsgrunnBeskrivelse: AvslagsgrunnBeskrivelse?,
    val halvGrunnbeløp: Int?,
)

enum class Avslagsgrunn {
    UFØRHET,
    FLYKTNING,
    OPPHOLDSTILLATELSE,
    PERSONLIG_OPPMØTE,
    FORMUE,
    BOR_OG_OPPHOLDER_SEG_I_NORGE,
    FOR_HØY_INNTEKT,
    SU_UNDER_MINSTEGRENSE,
    UTENLANDSOPPHOLD_OVER_90_DAGER,
    INNLAGT_PÅ_INSTITUSJON
}

enum class AvslagsgrunnBeskrivelse {
    UFØRHET_FLYKTNING,
    FORMUE,
    HØY_INNTEKT,
    UTLAND_OG_OPPHOLD,
    UNDER_MINSTEGRENSE
}
