package no.nav.su.se.bakover.institusjonsopphold.application.service

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.InstitusjonOgOppgaveHendelserPåSak
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdHendelsestype
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

/**
 * Generelt ønsker vi kun en åpen institusjonsoppgave per sak om gangen.
 * Dersom oppgaven er avsluttet, opprettet vi en ny.
 */
class OpprettOppgaverForInstitusjonsoppholdshendelser(
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val hendelseRepo: HendelseRepo,
    private val sakRepo: SakRepo,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) : Hendelseskonsument {

    override val konsumentId = HendelseskonsumentId("OpprettOppgaverForInstitusjonsoppholdshendelser")

    private val log = LoggerFactory.getLogger(this::class.java)

    fun opprettOppgaverForHendelser(
        correlationId: CorrelationId,
    ) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = InstitusjonsoppholdHendelsestype,
        ).forEach { (sakId, hendelseIder) ->
            prosesseserSak(sakId, hendelseIder, correlationId)
        }
        // Eventuelle exceptions fanges i jobben som kjører denne.
    }

    private fun prosesseserSak(sakId: UUID, uprosesserteHendelser: Nel<HendelseId>, correlationId: CorrelationId) {
        log.info("starter opprettelse av oppgaver for inst-hendelser på sak $sakId")

        // Merk at dette er alle oppgavehendelsene på saken, ikke bare de oppgavene som er knyttet til inst-hendelser.
        val alleOppgaveHendelser = oppgaveHendelseRepo.hentForSak(sakId)
        val alleInstHendelser = institusjonsoppholdHendelseRepo.hentForSak(sakId)
            ?: return Unit.also { log.debug("Sak {} har ingen inst-hendelser", sakId) }

        val instOgOppgaveHendelserPåSak = InstitusjonOgOppgaveHendelserPåSak(
            alleInstHendelser,
            alleOppgaveHendelser,
        )

        // Det kan være en mismatch mellom uprosesserte insthendelser (konsumentkøen) og hendelser som mangler oppgave.
        // Si f.eks. at vi har resatt konsumentkøen, så ønsker vi ikke lage nye oppgaver.
        // Vi bruker kun uprosesserteHendelser for å markere disse som prosessert.
        val hendelserSomManglerOppgaver = instOgOppgaveHendelserPåSak.hentInstHendelserSomManglerOppgave()
        when {
            hendelserSomManglerOppgaver.isEmpty() -> {
                log.info("Sak $sakId har ingen inst-hendelser som mangler oppgave")
                sessionFactory.withSessionContext { sx ->
                    merkSomProsessert(uprosesserteHendelser, sx)
                }
            }

            else -> {
                håndterHendelserSomManglerOppgave(
                    instOgOppgaveHendelserPåSak = instOgOppgaveHendelserPåSak,
                    sakId = sakId,
                    correlationId = correlationId,
                    uprosesserteHendelser = uprosesserteHendelser,
                    hendelserSomManglerOppgaver = hendelserSomManglerOppgaver.map { it.hendelseId }.toNonEmptyList(),
                )
            }
        }
    }

    private fun håndterHendelserSomManglerOppgave(
        instOgOppgaveHendelserPåSak: InstitusjonOgOppgaveHendelserPåSak,
        sakId: UUID,
        correlationId: CorrelationId,
        uprosesserteHendelser: Nel<HendelseId>,
        hendelserSomManglerOppgaver: Nel<HendelseId>,
    ) {
        instOgOppgaveHendelserPåSak.sisteOppgaveId()?.let { oppgaveId ->
            oppgaveService.hentOppgave(oppgaveId).onRight { oppgave ->
                if (oppgave.erÅpen()) {
                    log.info("Det finnes allerede en åpen institusjonsoppgave for sak $sakId, oppgaveId $oppgaveId, vi lagrer derfor ikke en ny oppgave.")
                    sessionFactory.withSessionContext { sx ->
                        merkSomProsessert(uprosesserteHendelser, sx)
                    }
                    return
                }
            }.onLeft {
                log.error(
                    "Feil ved henting av oppgave $oppgaveId, oppretter ny oppgave for institusjonshendelse",
                    RuntimeException("Trigger stacktrace"),
                )
            }
        }
        opprettOppgaveOgMerkSomProsessert(
            sakId = sakId,
            correlationId = correlationId,
            uprosesserteHendelser = uprosesserteHendelser,
            hendelserSomManglerOppgaver = hendelserSomManglerOppgaver,
        )
    }

    private fun opprettOppgaveOgMerkSomProsessert(
        sakId: UUID,
        correlationId: CorrelationId,
        uprosesserteHendelser: Nel<HendelseId>,
        hendelserSomManglerOppgaver: Nel<HendelseId>,
    ) {
        val sakInfo = sakRepo.hentSakInfo(sakId)
            ?: run {
                log.error("Feil ved henting av sak $sakId. Denne vil bli retried.")
                return
            }

        val nesteHendelsesversjon = (
            hendelseRepo.hentSisteVersjonFraEntitetId(sakInfo.sakId) ?: run {
                log.error(
                    "Fikk ikke noe hendelsesversjon ved henting fra entitetId (sakId) ${sakInfo.sakId}. Denne vil bli retried.",
                    RuntimeException("Trigger stacktrace"),
                )
                return
            }
            ).inc()

        val oppgaveCommand = lagOppgaveConfig(sakInfo, clock).getOrElse {
            return
        }
        val oppgaveResponse = oppgaveService.opprettOppgaveMedSystembruker(oppgaveCommand)
            .getOrElse {
                log.error(
                    "Fikk ikke opprettet oppgave for institusjonsopphold hendelser som mangler oppgave $hendelserSomManglerOppgaver for sak ${sakInfo.saksnummer}. Denne vil bli retried.",
                    RuntimeException("Trigger stacktrace"),
                )
                return
            }

        val opprettetOppgaveHendelse = OppgaveHendelse.Opprettet(
            hendelseId = HendelseId.generer(),
            sakId = sakInfo.sakId,
            versjon = nesteHendelsesversjon,
            oppgaveId = oppgaveResponse.oppgaveId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            meta = OppgaveHendelseMetadata(
                correlationId = correlationId,
                ident = null,
                brukerroller = listOf(),
                request = oppgaveResponse.request,
                response = oppgaveResponse.response,
            ),
            relaterteHendelser = hendelserSomManglerOppgaver,
            beskrivelse = oppgaveResponse.beskrivelse,
            oppgavetype = oppgaveResponse.oppgavetype,
        )
        sessionFactory.withTransactionContext { tx ->
            oppgaveHendelseRepo.lagre(
                hendelse = opprettetOppgaveHendelse,
                sessionContext = tx,
            )
            merkSomProsessert(uprosesserteHendelser, tx)
        }
    }

    private fun merkSomProsessert(
        hendelseIder: List<HendelseId>,
        sx: SessionContext,
    ) {
        hendelsekonsumenterRepo.lagre(
            hendelser = hendelseIder,
            konsumentId = konsumentId,
            context = sx,
        )
    }

    private fun lagOppgaveConfig(sakInfo: SakInfo, clock: Clock): Either<KunneIkkeHenteAktørId, OppgaveConfig> {
        return OppgaveConfig.Institusjonsopphold(
            saksnummer = sakInfo.saksnummer,
            sakstype = sakInfo.type,
            aktørId = personService.hentAktørIdMedSystembruker(sakInfo.fnr).getOrElse {
                log.error("Klarte ikke hente aktørId med systembruker for saksnummer ${sakInfo.saksnummer}. Denne vil bli prøvd på nytt.")
                return KunneIkkeHenteAktørId.left()
            },
            clock = clock,
        ).right()
    }

    private object KunneIkkeHenteAktørId
}
