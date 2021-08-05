package no.nav.su.se.bakover.service.hendelser

import no.nav.su.se.bakover.database.hendelse.HendelseRepo
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.PdlHendelse
import org.slf4j.LoggerFactory

// gjelder - ufør_flykning
// oppgavetype - vurder konsekvens om ytelse
// 1 uke frist på oppgaven
// se till att saksnummer kommer in i beskrivelsen
// 4815

class PersonhendelseService(
    private val personRepo: PersonRepo,
    private val hendelseRepo: HendelseRepo
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun prosesserNyMelding(pdlHendelse: PdlHendelse.Ny) {
        val eksisterendeSaksnummer = hentEksisterendeSaksnummer(pdlHendelse) ?: return

        log.info("Prosserer melding med offset: ${pdlHendelse.offset}, opplysningstype: $pdlHendelse")
        hendelseRepo.lagre(pdlHendelse, eksisterendeSaksnummer)
    }

    private fun hentEksisterendeSaksnummer(pdlHendelse: PdlHendelse.Ny): Saksnummer? =
        personRepo.hentSaksnummerForIdenter(pdlHendelse.personidenter)
}
