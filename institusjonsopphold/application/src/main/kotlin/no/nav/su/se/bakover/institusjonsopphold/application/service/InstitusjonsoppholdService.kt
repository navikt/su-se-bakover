package no.nav.su.se.bakover.institusjonsopphold.application.service

import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje.Companion.harInnvilgelse
import org.slf4j.LoggerFactory
import java.time.Clock

class InstitusjonsoppholdService(
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo,
    private val sakRepo: SakRepo,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun process(eksternHendelse: EksternInstitusjonsoppholdHendelse) {
        // toDomain burde kanskje skje fra presentation og inn?
        val hendelse = eksternHendelse.toDomain(clock)
        sakRepo.hentSaker(hendelse.eksternHendelse.norskident).ifEmpty {
            return Unit.also {
                sikkerLogg.debug("Forkaster institusjonsopphold hendelse ${hendelse.eksternHendelse.hendelseId} fordi den ikke er knyttet til sak")
            }
        }.single { it.vedtakstidslinje(Måned.now(clock)).harInnvilgelse() }.let {
            institusjonsoppholdHendelseRepo.lagre(hendelse.knyttTilSak(it.id))
        }
    }

//    fun opprettOppgaveForHendelser() {
//        institusjonsoppholdHendelseRepo.hentHendelserUtenOppgaveId().forEach {
//            val sak = sakRepo.hentSak()
//        }
//
//
//        val person = personService.hentPerson(sak.fnr).getOrElse {
//            return Unit.also { log.error("Fant ikke person for fnr: ${sak.fnr}, men vi hadde sak") }
//        }
//
//        oppgaveService.opprettOppgave(
//            OppgaveConfig.Institusjonsopphold(
//                saksnummer = sak.saksnummer,
//                sakstype = sak.type,
//                aktørId = person.ident.aktørId,
//                clock = clock,
//            ),
//        ).mapLeft {
//            log.error("Fikk ikke opprettet oppgave for institusjonsopphold hendelse ${hendelse.hendelseId} for sak ${sak.saksnummer}")
//        }.map {
//            TODO("skal vi gjøre noe med oppgave-id'en?")
//        }
//    }
}
