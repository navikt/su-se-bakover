package no.nav.su.se.bakover.institusjonsopphold.application.service

import arrow.core.getOrElse
import no.nav.su.se.bakover.common.extensions.whenever
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonOgOppgaveHendelserPåSak
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.hentSisteHendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje.Companion.harInnvilgelse
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdHendelsestype
import no.nav.su.se.bakover.oppgave.domain.HendelseJobbRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
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
            this.forEach { sak ->
                sak.vedtakstidslinje(Måned.now(clock)).harInnvilgelse().ifTrue {
                    institusjonsoppholdHendelseRepo.hentTidligereInstHendelserForOpphold(sak.id, hendelse.oppholdId)
                        .whenever(
                            isEmpty = {
                                institusjonsoppholdHendelseRepo
                                    .lagre(hendelse.nyHendelsePåSak(sak.id, sak.versjon.inc(), clock))
                            },
                            isNotEmpty = {
                                val sisteHendelsesVersjon =
                                    hendelseRepo.hentSisteVersjonFraEntitetId(sak.id)
                                        ?: throw IllegalStateException("Fant ikke siste hendelses versjon for sak ${sak.id} ved eksterne inst hendelse ${hendelse.hendelseId}")
                                institusjonsoppholdHendelseRepo
                                    .lagre(
                                        hendelse.nyHendelsePåSakLenketTilEksisterendeHendelse(
                                            it.hentSisteHendelse(),
                                            sisteHendelsesVersjon.inc(),
                                            clock,
                                        ),
                                    )
                            },
                        )
                }
            }
        }
    }

    fun opprettOppgaveForHendelser(jobbNavn: String) {
        try {
            hendelseJobbRepo.hentSakIdOgHendelseIderForNavnOgType(
                jobbNavn = jobbNavn,
                hendelsestype = InstitusjonsoppholdHendelsestype,
            ).map { (sakId, _) ->
                val oppgaveHendelserPåSak = oppgaveHendelseRepo.hentForSak(sakId)
                val alleInstHendelserPåSak = institusjonsoppholdHendelseRepo.hentForSak(sakId)
                    ?: return Unit.also { log.debug("Sak {} har ingen inst-hendelser", sakId) }

                val instOgOppgaveHendelserPåSak = InstitusjonOgOppgaveHendelserPåSak(
                    alleInstHendelserPåSak,
                    oppgaveHendelserPåSak,
                )

                instOgOppgaveHendelserPåSak.hentInstHendelserSomManglerOppgave().ifNotEmpty {
                    val sakInfo = sakRepo.hentSakInfo(sakId)
                        ?: throw IllegalStateException("Feil ved henting av sak $sakId")
                    sessionFactory.withTransactionContext { tx ->

                        val oppgaveId = oppgaveService.opprettOppgave(lagOppgaveConfig(sakInfo, clock))
                            .getOrElse {
                                log.error("Fikk ikke opprettet oppgave for institusjonsopphold hendelser ${this.map { it.hendelseId }} for sak ${sakInfo.saksnummer}")
                                return@withTransactionContext
                            }

                        this.forEach {
                            val hendelsesversjon = hendelseRepo.hentSisteVersjonFraEntitetId(sakInfo.sakId)?.inc()
                                ?: throw IllegalStateException("Fikk ikke noe hendelsesversjon ved henting fra entitetId (sakId) ${sakInfo.sakId} for hendelse ${it.hendelseId}. Oppgave ble laget med oppgaveId $oppgaveId")

                            val tidligereOppgaveHendelse =
                                instOgOppgaveHendelserPåSak.hentHendelserMedSammeOppholdId(it.eksterneHendelse.oppholdId)?.second?.maxByOrNull { it.versjon }

                            oppgaveHendelseRepo.lagre(
                                it.nyOppgaveHendelse(oppgaveId, tidligereOppgaveHendelse, hendelsesversjon, clock),
                                tx,
                            )
                        }
                        hendelseJobbRepo.lagre(
                            hendelser = this.map { it.hendelseId },
                            jobbNavn = jobbNavn,
                            context = tx,
                        )
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

    private fun lagOppgaveConfig(sakInfo: SakInfo, clock: Clock): OppgaveConfig = OppgaveConfig.Institusjonsopphold(
        saksnummer = sakInfo.saksnummer,
        sakstype = sakInfo.type,
        aktørId = hentAktørId(sakInfo.fnr),
        clock = clock,
    )
}
