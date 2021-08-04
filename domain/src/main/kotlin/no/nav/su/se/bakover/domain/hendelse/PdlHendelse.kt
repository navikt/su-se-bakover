package no.nav.su.se.bakover.domain.hendelse

import no.nav.su.se.bakover.domain.AktørId
import java.time.LocalDate

sealed class PdlHendelse {
    enum class Endringstype {
        OPPRETTET,
        KORRIGERT,
        ANNULLERT,
        OPPHOERT,
    }

    abstract val hendelseId: String
    abstract val gjeldendeAktørId: AktørId
    abstract val endringstype: Endringstype
    abstract val offset: Long
    abstract val personidenter: List<String>

    data class Dødsfall(
        override val hendelseId: String,
        override val gjeldendeAktørId: AktørId,
        override val endringstype: Endringstype,
        override val offset: Long,
        override val personidenter: List<String>,
        val dødsdato: LocalDate,
    ) : PdlHendelse()

    data class UtflyttingFraNorge(
        override val hendelseId: String,
        override val gjeldendeAktørId: AktørId,
        override val endringstype: Endringstype,
        override val offset: Long,
        override val personidenter: List<String>,
        val utflyttingsdato: LocalDate,
    ) : PdlHendelse()
}
