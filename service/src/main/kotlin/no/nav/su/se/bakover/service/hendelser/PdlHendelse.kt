package no.nav.su.se.bakover.service.hendelser

import java.time.LocalDate

data class PdlHendelse(
    val hendelseId: String,
    val gjeldendeAktørId: String?,
    val offset: Long,
    val opplysningstype: String,
    val endringstype: String,
    val personIdenter: List<String>,
    val dødsdato: LocalDate? = null,
    val utflyttingsdato: LocalDate? = null,
)
