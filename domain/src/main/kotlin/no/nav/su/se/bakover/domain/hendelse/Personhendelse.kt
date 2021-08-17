package no.nav.su.se.bakover.domain.hendelse

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import java.util.UUID

sealed class Personhendelse {
    enum class Endringstype(val value: String) {
        OPPRETTET("OPPRETTET"),
        KORRIGERT("KORRIGERT"),
        ANNULLERT("ANNULLERT"),
        OPPHOERT("OPPHOERT"),
    }
    abstract val gjeldendeAktørId: AktørId
    abstract val endringstype: Endringstype
    abstract val hendelse: Hendelse

    data class Ny(
        val hendelseId: String,
        override val gjeldendeAktørId: AktørId,
        override val endringstype: Endringstype,
        override val hendelse: Hendelse,
        val offset: Long,
        val personidenter: List<String>,
    ) : Personhendelse()

    data class TilknyttetSak(
        val id: UUID,
        override val gjeldendeAktørId: AktørId,
        override val endringstype: Endringstype,
        override val hendelse: Hendelse,
        val saksnummer: Saksnummer,
        val sakId: UUID,
        val oppgaveId: OppgaveId?,
    ) : Personhendelse()

    sealed class Hendelse {
        data class Dødsfall(val dødsdato: LocalDate?) : Hendelse()

        // TODO jah: Her finnes det 2 felter vi ignorerer: [tilflyttingsland, tilflyttingsstedIUtlandet], finn ut om det er tiltenkt eller ikke.
        data class UtflyttingFraNorge(val utflyttingsdato: LocalDate?) : Hendelse()
    }
}
