package no.nav.su.se.bakover.domain.hendelse

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import java.util.UUID

sealed class Personhendelse {
    enum class Endringstype {
        OPPRETTET,
        KORRIGERT,
        ANNULLERT,
        OPPHØRT,
    }
    abstract val gjeldendeAktørId: AktørId
    abstract val endringstype: Endringstype
    abstract val hendelse: Hendelse

    data class Ny(
        override val gjeldendeAktørId: AktørId,
        override val endringstype: Endringstype,
        override val hendelse: Hendelse,
        val personidenter: List<String>,
        val metadata: Metadata,
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

    /** Metadata rundt hendelsen
     * Et offset er kun unikt kombinert med partisjonen (direkte tilknyttet Kafka)
     * */
    data class Metadata(
        val hendelseId: String,
        val tidligereHendelseId: String?,
        val offset: Long,
        val partisjon: Int,
        /** f.eks. FREG */
        val master: String,
    )
}
