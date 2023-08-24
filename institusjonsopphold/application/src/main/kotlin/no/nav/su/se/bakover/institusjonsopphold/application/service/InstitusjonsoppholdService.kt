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
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Behandlinger.Companion.harBehandlingUnderArbeid
import no.nav.su.se.bakover.domain.hentSisteHendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje.Companion.harInnvilgelse
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje.Companion.harStans
import no.nav.su.se.bakover.hendelse.domain.HendelseActionRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdHendelsestype
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
    private val hendelseActionRepo: HendelseActionRepo,
    private val hendelseRepo: HendelseRepo,
    private val sakRepo: SakRepo,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun process(hendelse: EksternInstitusjonsoppholdHendelse) {
        sakRepo.hentSaker(hendelse.norskident).ifNotEmpty {
            this.forEach { sak ->
                sak.harBehandlingUnderArbeidEllerVedtakSomGirGrunnlagForInstHendelse(clock).ifTrue {
                    institusjonsoppholdHendelseRepo.hentTidligereInstHendelserForOpphold(sak.id, hendelse.oppholdId)
                        .whenever(
                            isEmpty = {
                                institusjonsoppholdHendelseRepo
                                    .lagre(hendelse.nyHendelsePåSak(sak.id, sak.versjon.inc(), clock))
                            },
                            isNotEmpty = {
                                institusjonsoppholdHendelseRepo
                                    .lagre(
                                        hendelse.nyHendelsePåSakLenketTilEksisterendeHendelse(
                                            it.hentSisteHendelse(),
                                            sak.versjon.inc(),
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
            hendelseActionRepo.hentSakOgHendelsesIderSomIkkeHarKjørtAction(
                action = jobbNavn,
                hendelsestype = InstitusjonsoppholdHendelsestype,
            ).map { (sakId, _) ->
                log.info("starter opprettelse av oppgaver for inst-hendelser på sak $sakId")

                val alleOppgaveHendelser = oppgaveHendelseRepo.hentForSak(sakId).let {
                    log.info("hentet alle oppgave hendelser på sak")
                    it
                }

                val alleInstHendelser = institusjonsoppholdHendelseRepo.hentForSak(sakId)
                    ?: return Unit.also { log.debug("Sak {} har ingen inst-hendelser", sakId) }

                val instOgOppgaveHendelserPåSak = InstitusjonOgOppgaveHendelserPåSak(
                    alleInstHendelser,
                    alleOppgaveHendelser,
                )
                log.info("laget instOgOppgaveHendelserPåSak")

                instOgOppgaveHendelserPåSak.hentInstHendelserSomManglerOppgave().ifNotEmpty {
                    log.info("Hentet alle inst hendelser som mangler oppgave")

                    val sakInfo = sakRepo.hentSakInfo(sakId)
                        ?: throw IllegalStateException("Feil ved henting av sak $sakId")
                    log.info("Hentet sakInfo")
                    sessionFactory.withTransactionContext { tx ->
                        log.info("starter transaction")

                        val oppgaveConfig = lagOppgaveConfig(sakInfo, clock)
                        log.info("oppgaveConfig laget")

                        val oppgaveId = oppgaveService.opprettOppgaveMedSystembruker(oppgaveConfig)
                            .getOrElse {
                                log.error("Fikk ikke opprettet oppgave for institusjonsopphold hendelser ${this.map { it.hendelseId }} for sak ${sakInfo.saksnummer}")
                                return@withTransactionContext
                            }
                        log.info("Laget oppgave id for hendelser")

                        this.forEach {
                            val hendelsesversjon = hendelseRepo.hentSisteVersjonFraEntitetId(sakInfo.sakId, tx)?.inc()
                                ?: throw IllegalStateException("Fikk ikke noe hendelsesversjon ved henting fra entitetId (sakId) ${sakInfo.sakId} for hendelse ${it.hendelseId}. Oppgave ble laget med oppgaveId $oppgaveId")

                            val tidligereOppgaveHendelse =
                                instOgOppgaveHendelserPåSak.hentHendelserMedSammeOppholdId(it.eksterneHendelse.oppholdId)?.second?.maxByOrNull { it.versjon }
                            log.info("Laget tidligere oppgave hendelse med resultat $tidligereOppgaveHendelse")

                            oppgaveHendelseRepo.lagre(
                                it.nyOppgaveHendelse(oppgaveId, tidligereOppgaveHendelse, hendelsesversjon, clock),
                                tx,
                            )
                            log.info("lagret ny oppgave hendelse")
                        }
                        hendelseActionRepo.lagre(
                            hendelser = this.map { it.hendelseId },
                            action = jobbNavn,
                            context = tx,
                        )
                        log.info("lagret hendelsene i hendelseActionRepo")
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Feil skjedde ved oppretting av oppgave for jobb $jobbNavn. originalFeil $e", e)
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

    private fun Sak.harBehandlingUnderArbeidEllerVedtakSomGirGrunnlagForInstHendelse(clock: Clock): Boolean {
        val tidslinje = this.vedtakstidslinje(Måned.now(clock))
        val harInnvilgetVedtak = tidslinje.harInnvilgelse()
        val harStansetVedtak = tidslinje.harStans()
        val harBehandlingUnderArbeid = this.behandlinger.søknadsbehandlinger.harBehandlingUnderArbeid()

        return harInnvilgetVedtak || harStansetVedtak || harBehandlingUnderArbeid
    }
}
