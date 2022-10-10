package no.nav.su.se.bakover.hendelse.domain

data class HendelseMetadata(
    val correlationId: String,
    val ident: String,
    // val rettigheter: String, TODO jah: Vurder legg på dette.
)
