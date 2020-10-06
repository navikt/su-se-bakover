package no.nav.su.se.bakover.domain

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import java.util.UUID

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
    val satsGrunn: Satsgrunn?,
    val redusertStønadStatus: Boolean,
    val harEktefelle: Boolean?,
    val fradrag: List<FradragPerMåned>,
    val fradragSum: Int,
    val status: Behandling.BehandlingsStatus,
    val avslagsgrunn: Avslagsgrunn?,
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

enum class Satsgrunn {
    ENSLIG,
    DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
}

data class FradragPerMåned(val type: Fradragstype, val beløp: Int)


data class SlettBehandlingBody(
    val sakId: UUID,
    val søknadId: UUID,
    val avsluttetBegrunnelse: AvsluttetBegrunnelse
) {
    fun valid() = avsluttetBegrunnelse == AvsluttetBegrunnelse.AvvistSøktForTidlig ||
        avsluttetBegrunnelse == AvsluttetBegrunnelse.Bortfalt ||
        avsluttetBegrunnelse == AvsluttetBegrunnelse.Trukket
}

enum class AvsluttetBegrunnelse {
    Trukket,
    Bortfalt,
    AvvistSøktForTidlig;

    companion object {
        fun isValid(s: String) =
            runBlocking {
                Either.catch { valueOf(s) }
                    .isRight()
            }
    }
}

