package no.nav.su.se.bakover.database.hendelse

import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.PdlHendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.apache.kafka.clients.consumer.ConsumerRecord

interface HendelseRepo {
    fun lagre(pdlHendelse: PdlHendelse, saksnummer: Saksnummer, melding: String)
    fun hent(hendelseId: String)
    fun oppdaterOppgave(hendelseId: String, oppgaveId: OppgaveId)
}
