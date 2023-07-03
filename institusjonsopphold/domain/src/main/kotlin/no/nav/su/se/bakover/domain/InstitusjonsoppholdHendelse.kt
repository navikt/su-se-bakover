package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.person.Fnr

data class InstitusjonsoppholdHendelse(
    val hendelseId: Long,
    val oppholdId: Long,
    val norskident: Fnr,
    val type: InstitusjonsoppholdType,
    val kilde: InstitusjonsoppholdKilde,
)
