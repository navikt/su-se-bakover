package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.NavIdentBruker

data class HendelseMetadata(
    val correlationId: CorrelationId?,
    val ident: NavIdentBruker?,
    // val rettigheter: String, TODO jah: Vurder legg på dette.
)
