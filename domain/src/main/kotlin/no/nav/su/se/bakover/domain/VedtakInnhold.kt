package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.beregning.FradragDto

data class VedtakInnhold(
    val dato: String,
    val fødselsnummer: Fnr,
    val fornavn: String,
    val etternavn: String,
    val adresse: String?,
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
    val fradrag: List<FradragDto>,
    val fradragSum: Int,
    val status: Behandling.Status.BehandlingsStatus
)
