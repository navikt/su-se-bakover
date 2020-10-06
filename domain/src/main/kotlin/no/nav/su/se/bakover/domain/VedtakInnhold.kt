package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.beregning.Fradragstype

sealed class VedtakInnhold {
    abstract val dato: String
    abstract val fødselsnummer: Fnr
    abstract val fornavn: String
    abstract val etternavn: String
    abstract val adresse: String?
    abstract val husnummer: String?
    abstract val bruksenhet: String?
    abstract val postnummer: String?
    abstract val poststed: String

    data class Innvilgelsesvedtak(
        override val dato: String,
        override val fødselsnummer: Fnr,
        override val fornavn: String,
        override val etternavn: String,
        override val adresse: String?,
        override val husnummer: String?,
        override val bruksenhet: String?,
        override val postnummer: String?,
        override val poststed: String,
        val månedsbeløp: Int,
        val fradato: String,
        val tildato: String,
        val sats: String,
        val satsbeløp: Int,
        val satsGrunn: Satsgrunn,
        val redusertStønadStatus: Boolean,
        val harEktefelle: Boolean,
        val fradrag: List<FradragPerMåned>,
        val fradragSum: Int,
    ) : VedtakInnhold()

    data class Avslagsvedtak(
        override val dato: String,
        override val fødselsnummer: Fnr,
        override val fornavn: String,
        override val etternavn: String,
        override val adresse: String?,
        override val husnummer: String?,
        override val bruksenhet: String?,
        override val postnummer: String?,
        override val poststed: String,
        val avslagsgrunn: Avslagsgrunn,
        val halvGrunnbeløp: Int
    ) : VedtakInnhold()
}

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

enum class Satsgrunn {
    ENSLIG,
    DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
}

data class FradragPerMåned(val type: Fradragstype, val beløp: Int)
