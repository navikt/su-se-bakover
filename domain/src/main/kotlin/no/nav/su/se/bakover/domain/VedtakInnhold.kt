package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.beregning.Sats

data class VedtakInnhold(
    val dato: String,
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val adresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val månedsbeløp: Int?,
    val fradato: String?,
    val tildato: String?,
    val nysøkdato: String?,
    val sats: Sats?,
    val satsbeløp: Int?,
    val status: Behandling.BehandlingsStatus
)
