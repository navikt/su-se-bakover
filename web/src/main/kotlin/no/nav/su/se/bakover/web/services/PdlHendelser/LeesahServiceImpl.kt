package no.nav.su.se.bakover.web.services.PdlHendelser

import arrow.core.getOrHandle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import java.lang.RuntimeException

// gjelder - ufør_flykning
// oppgavetype - vurder konsekvens om ytelse
// 1 uke frist på oppgaven
// se till att saksnummer kommer in i beskrivelsen
// 4815

internal class LeesahServiceImpl(
    private val oppgaveService: OppgaveService,
    private val sakService: SakService,
    private val personService: PersonService,
) : LeesahService {
    override fun prosesserNyMelding(pdlHendelse: PdlHendelse) {
        when (pdlHendelse.opplysningstype) {
            LeesahService.Opplysningstype.DØDSFALL.value -> hanterDødsfallHendelse(pdlHendelse)
            LeesahService.Opplysningstype.UTFLYTTING_FRA_NORGE.value -> {}
        }
    }

    private fun hanterDødsfallHendelse(pdlHendelse: PdlHendelse) {
        lagRevurderingsOppgave(Fnr(pdlHendelse.personIdenter.first()))
    }

    private fun lagRevurderingsOppgave(fnr: Fnr) {
        val sak = sakService.hentSak(fnr).getOrHandle { throw RuntimeException("") }
        val person = personService.hentPerson(sak.fnr).getOrHandle { throw RuntimeException("") }

        oppgaveService.opprettOppgave(OppgaveConfig.Revurderingsbehandling(sak.saksnummer, person.ident.aktørId))
    }
}
