package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.domain.PdfA

data class HendelseFil(
    val hendelseId: HendelseId,
    // denne skal i utgangspunktet søtte forskjellige ting. per nå så har vi bare Pdf - så slipper intellij å klage
    val fil: PdfA,
)
