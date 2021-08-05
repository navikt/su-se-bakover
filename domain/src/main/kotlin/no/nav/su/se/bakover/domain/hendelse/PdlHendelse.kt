package no.nav.su.se.bakover.domain.hendelse

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate

sealed class PdlHendelse {
    enum class Endringstype(val value: String) {
        OPPRETTET("OPPRETTET"),
        KORRIGERT("KORRIGERT"),
        ANNULLERT("ANNULLERT"),
        OPPHOERT("OPPHOERT"),
    }

    abstract val hendelseId: String
    abstract val gjeldendeAktørId: AktørId
    abstract val endringstype: Endringstype
    abstract val hendelse: Hendelse

    data class Ny(
        override val hendelseId: String,
        override val gjeldendeAktørId: AktørId,
        override val endringstype: Endringstype,
        override val hendelse: Hendelse,
        val offset: Long,
        val personidenter: List<String>,
    ) : PdlHendelse()

    data class Persistert(
        override val hendelseId: String,
        override val gjeldendeAktørId: AktørId,
        override val endringstype: Endringstype,
        override val hendelse: Hendelse,
        val saksnummer: Saksnummer,
        val oppgaveId: OppgaveId?,
    ) : PdlHendelse()

    sealed class Hendelse {
        data class Dødsfall(val dødsdato: LocalDate) : Hendelse()
        data class UtflyttingFraNorge(val utflyttingsdato: LocalDate) : Hendelse()
    }
}
