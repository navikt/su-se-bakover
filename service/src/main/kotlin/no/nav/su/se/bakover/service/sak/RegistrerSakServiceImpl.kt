package no.nav.su.se.bakover.service.sak

import arrow.core.Ior
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.RegistrerSøknadCommand
import no.nav.su.se.bakover.domain.sak.HentSakRepo
import no.nav.su.se.bakover.domain.sak.RegistrerSakRepo
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent

// TODO jah: Foreslår å kutte ut AccessCheckProxy (og fjerne interfacet RegistrerSakService) og flytte den logikken inn hit.
//  En mulighet er å flytte det helt inn i domenet, siden kode 6/7/egen ansatt er et domeneanliggende, men det kan bli for tungvindt?
class RegistrerSakServiceImpl(
    private val registrerSakRepo: RegistrerSakRepo,
    private val hentSakRepo: HentSakRepo,
    private val sakFactory: SakFactory,
) {
    fun opprettSak(command: RegistrerSøknadCommand): Ior<PersonenHarAlleredeEnTilknyttetSak,Sak> {
        hentSakRepo.hentSak(command.innsendtFnr, command.sakstype)?.let {
            return Ior.Both(PersonenHarAlleredeEnTilknyttetSak,it)
        }
        sakFactory.registrerSøknad()
        sakRepo.reg(sak,).also {
            hentSak(sak.id).fold(
                ifLeft = { log.error("Opprettet sak men feilet ved henting av den.") },
                ifRight = {
                    observers.forEach { observer -> observer.handle(StatistikkEvent.SakOpprettet(it)) }
                },
            )
        }
    }
}

object PersonenHarAlleredeEnTilknyttetSak
