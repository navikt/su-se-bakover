package no.nav.su.se.bakover.institusjonsopphold.application.service

import arrow.core.getOrElse
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje.Companion.harInnvilgelse
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.oppgave.HendelseJobbRepo
import no.nav.su.se.bakover.hendelse.domain.oppgave.OppgaveHendelseRepo
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdHendelsestype
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Clock

class InstitusjonsoppholdService(
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val hendelseJobbRepo: HendelseJobbRepo,
    private val hendelseRepo: HendelseRepo,
    private val sakRepo: SakRepo,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun process(hendelse: EksternInstitusjonsoppholdHendelse) {
        sakRepo.hentSaker(hendelse.norskident).ifNotEmpty {
            this.forEach {
                it.vedtakstidslinje(Måned.now(clock)).harInnvilgelse().ifTrue {
                    institusjonsoppholdHendelseRepo.lagre(hendelse.nyHendelseMedSak(it.id, it.versjon.inc(), clock))
                }
            }
        }
    }

    fun opprettOppgaveForHendelser(jobbNavn: String) {
        try {
            hendelseJobbRepo.hentSakIdOgHendelseIderForNavnOgType(
                jobbNavn = jobbNavn,
                hendelsestype = InstitusjonsoppholdHendelsestype,
            ).map { (sakId, hendelseIder) ->
                val relevanteOppgaver = oppgaveHendelseRepo.hentForSak(sakId).filter { it.triggetAv in hendelseIder }
                val alleInstitusjonsHendelserForSaken = institusjonsoppholdHendelseRepo.hentForSak(sakId)
                if (alleInstitusjonsHendelserForSaken == null) {
                    log.debug("ingen nye inst-hendelser for sak {}. Avslutter jobb.", sakId)
                    return
                }
                alleInstitusjonsHendelserForSaken
                    .filter { it.hendelseId in hendelseIder }
                    .filterNot { it.hendelseId in relevanteOppgaver.map { it.triggetAv } }
                    .ifNotEmpty {
                        val sakInfo =
                            sakRepo.hentSakInfo(sakId) ?: throw IllegalStateException("Feil ved henting av sak $sakId")
                        sessionFactory.withTransactionContext { tx ->
                            opprettOppgave(
                                sakInfo = sakInfo,
                                aktørId = hentAktørId(sakInfo.fnr),
                                hendelser = this,
                                tx = tx,
                            )
                            hendelseJobbRepo.lagre(hendelser = hendelseIder, jobbNavn = jobbNavn, context = tx)
                        }
                    }
            }
        } catch (e: Exception) {
            log.error("Feil skjedde ved oppretting av oppgave for jobb $jobbNavn. originalFeil $e")
        }
    }

    private fun hentAktørId(fnr: Fnr): AktørId = personService.hentAktørId(fnr).getOrElse {
        throw IllegalStateException("Feil ved henting av person $fnr")
    }

    private fun opprettOppgave(
        sakInfo: SakInfo,
        aktørId: AktørId,
        hendelser: List<InstitusjonsoppholdHendelse>,
        tx: TransactionContext,
    ) {
        oppgaveService.opprettOppgave(
            OppgaveConfig.Institusjonsopphold(
                saksnummer = sakInfo.saksnummer,
                sakstype = sakInfo.type,
                aktørId = aktørId,
                clock = clock,
            ),
        ).mapLeft {
            log.error("Fikk ikke opprettet oppgave for institusjonsopphold hendelser ${hendelser.map { it.hendelseId }} for sak ${sakInfo.saksnummer}")
        }.map { oppgaveId ->
            hendelser.forEach {
                val hendelsesversjon = hendelseRepo.hentSisteVersjonFraEntitetId(sakInfo.sakId)?.inc()
                    ?: throw IllegalStateException("Fikk ikke noe hendelsesversjon ved henting fra entitetId (sakId) ${sakInfo.sakId} for hendelse ${it.hendelseId}. Oppgave ble laget med oppgaveId $oppgaveId")

                oppgaveHendelseRepo.lagre(it.nyOppgaveHendelse(oppgaveId, hendelsesversjon, clock), tx)
            }
        }
    }
}
