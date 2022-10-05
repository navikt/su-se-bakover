package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import no.nav.su.se.bakover.domain.kontrollsamtale.UgyldigStatusovergang
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleService {
    fun kallInn(
        sakId: UUID,
        kontrollsamtale: Kontrollsamtale,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit>

    fun nyDato(sakId: UUID, dato: LocalDate): Either<KunneIkkeSetteNyDatoForKontrollsamtale, Unit>
    fun hentNestePlanlagteKontrollsamtale(
        sakId: UUID,
        sessionContext: SessionContext = defaultSessionContext(),
    ): Either<KunneIkkeHenteKontrollsamtale, Kontrollsamtale>

    fun hentPlanlagteKontrollsamtaler(sessionContext: SessionContext = defaultSessionContext()): Either<KunneIkkeHenteKontrollsamtale, List<Kontrollsamtale>>
    fun opprettPlanlagtKontrollsamtale(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling,
        sessionContext: SessionContext,
    ): OpprettPlanlagtKontrollsamtaleResultat

    fun annullerKontrollsamtale(
        sakId: UUID,
        sessionContext: SessionContext,
    ): Either<UgyldigStatusovergang, AnnulerKontrollsamtaleResultat>

    fun defaultSessionContext(): SessionContext
}

class KontrollsamtaleServiceImpl(
    private val sakService: SakService,
    private val personService: PersonService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val kontrollsamtaleRepo: KontrollsamtaleRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : KontrollsamtaleService {

    override fun kallInn(
        sakId: UUID,
        kontrollsamtale: Kontrollsamtale,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit> {
        val sak = sakService.hentSak(sakId).getOrElse {
            log.error("Fant ikke sak for sakId $sakId")
            return KunneIkkeKalleInnTilKontrollsamtale.FantIkkeSak.left()
        }

        val gjeldendeStønadsperiode = sak.hentGjeldendeStønadsperiode(clock).rightIfNotNull { }.getOrElse {
            log.error("Fant ingen gjeldende stønadsperiode på sakId $sakId")
            return KunneIkkeKalleInnTilKontrollsamtale.FantIkkeGjeldendeStønadsperiode.left()
        }

        val person = personService.hentPersonMedSystembruker(sak.fnr).getOrElse {
            log.error("Fant ikke person for fnr: ${sak.fnr}")
            return KunneIkkeKalleInnTilKontrollsamtale.FantIkkePerson.left()
        }

        val dokument = lagDokument(person, sakId, sak.saksnummer).getOrElse {
            log.error("Klarte ikke lage dokument for innkalling til kontrollsamtale på sakId $sakId")
            return KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeGenerereDokument.left()
        }

        return Either.catch {
            sessionFactory.withTransactionContext { transactionContext ->
                brevService.lagreDokument(dokument, transactionContext)
                kontrollsamtaleRepo.lagre(
                    kontrollsamtale = kontrollsamtale.settInnkalt(dokument.id).getOrElse {
                        throw RuntimeException("Kontrollsamtale er i ugyldig tilstand for å bli satt til innkalt")
                    },
                    sessionContext = transactionContext,
                )
                Kontrollsamtale.opprettNyKontrollsamtale(gjeldendeStønadsperiode, sakId, clock).map {
                    kontrollsamtaleRepo.lagre(it, transactionContext)
                }
            }
            oppgaveService.opprettOppgaveMedSystembruker(
                config = OppgaveConfig.Kontrollsamtale(
                    saksnummer = sak.saksnummer,
                    aktørId = person.ident.aktørId,
                    clock = clock,
                ),
            ).getOrElse {
                throw RuntimeException("Fikk ikke opprettet oppgave").also {
                    log.error("Fikk ikke opprettet oppgave")
                }
            }
        }.fold(
            ifLeft = {
                log.error("Klarte ikke kalle inn kontrollsamtale for sakId $sakId", it)
                KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeKalleInn.left()
            },
            ifRight = {
                log.info("Kontrollsamtale kalt inn for sakId $sakId")
                Unit.right()
            },
        )
    }

    override fun nyDato(sakId: UUID, dato: LocalDate): Either<KunneIkkeSetteNyDatoForKontrollsamtale, Unit> {
        val sak = sakService.hentSak(sakId).getOrElse {
            log.error("Fant ikke sak for sakId $sakId")
            return KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeSak.left()
        }
        sak.harGjeldendeEllerFremtidigStønadsperiode(clock).ifFalse {
            log.info("Fant ingen gjeldende stønadsperiode på sakId $sakId")
            return KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeGjeldendeStønadsperiode.left()
        }

        return hentNestePlanlagteKontrollsamtale(sakId).fold(
            ifLeft = {
                kontrollsamtaleRepo.lagre(
                    Kontrollsamtale.opprettNyKontrollsamtale(
                        sakId = sakId,
                        innkallingsdato = dato,
                        clock = clock,
                    ),
                ).right()
            },
            ifRight = { kontrollsamtale ->
                kontrollsamtale.endreDato(dato).map { endretKontrollsamtale ->
                    kontrollsamtaleRepo.lagre(endretKontrollsamtale)
                }.mapLeft {
                    when (it) {
                        Kontrollsamtale.KunneIkkeEndreDato.DatoErIkkeFørsteIMåned -> KunneIkkeSetteNyDatoForKontrollsamtale.DatoIkkeFørsteIMåned
                        Kontrollsamtale.KunneIkkeEndreDato.UgyldigStatusovergang -> KunneIkkeSetteNyDatoForKontrollsamtale.UgyldigStatusovergang
                    }
                }
            },
        )
    }

    override fun hentNestePlanlagteKontrollsamtale(
        sakId: UUID,
        sessionContext: SessionContext,
    ): Either<KunneIkkeHenteKontrollsamtale.FantIkkeKontrollsamtale, Kontrollsamtale> {
        val samtaler = kontrollsamtaleRepo.hentForSakId(sakId, sessionContext).sortedBy { it.innkallingsdato }
        return samtaler.find { it.status === Kontrollsamtalestatus.PLANLAGT_INNKALLING }?.right()
            ?: KunneIkkeHenteKontrollsamtale.FantIkkeKontrollsamtale.left()
    }

    override fun hentPlanlagteKontrollsamtaler(sessionContext: SessionContext): Either<KunneIkkeHenteKontrollsamtale, List<Kontrollsamtale>> =
        Either.catch { kontrollsamtaleRepo.hentAllePlanlagte(LocalDate.now(clock), sessionContext) }.mapLeft {
            log.error("Kunne ikke hente planlagte kontrollsamtaler før ${LocalDate.now(clock)}", it)
            return KunneIkkeHenteKontrollsamtale.KunneIkkeHenteKontrollsamtaler.left()
        }

    override fun opprettPlanlagtKontrollsamtale(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling,
        sessionContext: SessionContext,
    ): OpprettPlanlagtKontrollsamtaleResultat {
        return hentNestePlanlagteKontrollsamtale(vedtak.behandling.sakId, sessionContext).fold(
            {
                Kontrollsamtale.opprettNyKontrollsamtaleFraVedtak(vedtak, clock).fold(
                    {
                        log.info("Skal ikke planlegge kontrollsamtale for sak ${vedtak.behandling.sakId} og vedtak ${vedtak.id}")
                        OpprettPlanlagtKontrollsamtaleResultat.SkalIkkePlanleggeKontrollsamtale
                    },
                    {
                        log.info("Opprettet planlagt kontrollsamtale $it for vedtak ${vedtak.id}")
                        kontrollsamtaleRepo.lagre(it, sessionContext)
                        OpprettPlanlagtKontrollsamtaleResultat.Opprettet(it)
                    },
                )
            },
            {
                log.info("Planlagt kontrollsamtale finnes allerede for sak ${vedtak.behandling.sakId} og vedtak ${vedtak.id}")
                OpprettPlanlagtKontrollsamtaleResultat.PlanlagtKontrollsamtaleFinnesAllerede(it)
            },
        )
    }

    override fun annullerKontrollsamtale(
        sakId: UUID,
        sessionContext: SessionContext,
    ): Either<UgyldigStatusovergang, AnnulerKontrollsamtaleResultat> {
        return hentNestePlanlagteKontrollsamtale(sakId, sessionContext).fold(
            {
                log.info("Fant ingen planlagt kontrollsamtale for sakId $sakId")
                AnnulerKontrollsamtaleResultat.IngenKontrollsamtaleÅAnnulere.right()
            },
            {
                it.annuller().map { annullertKontrollSamtale ->
                    kontrollsamtaleRepo.lagre(annullertKontrollSamtale, sessionContext)
                    AnnulerKontrollsamtaleResultat.AnnulertKontrollsamtale(annullertKontrollSamtale)
                }.mapLeft {
                    UgyldigStatusovergang
                }
            },
        )
    }

    private fun lagDokument(
        person: Person,
        sakId: UUID,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeLageBrev, Dokument.MedMetadata.Informasjon> {
        val brevRequest = LagBrevRequest.InnkallingTilKontrollsamtale(
            person = person,
            dagensDato = LocalDate.now(clock),
            saksnummer = saksnummer,
        )
        return brevService.lagBrev(brevRequest).map {
            Dokument.UtenMetadata.Informasjon.Viktig(
                opprettet = Tidspunkt.now(clock),
                tittel = brevRequest.brevInnhold.brevTemplate.tittel(),
                generertDokument = it,
                generertDokumentJson = brevRequest.brevInnhold.toJson(),
            ).leggTilMetadata(
                metadata = Dokument.Metadata(
                    sakId = sakId,
                    bestillBrev = true,
                ),
            )
        }
    }

    override fun defaultSessionContext(): SessionContext = sessionFactory.newSessionContext()
}

sealed interface KunneIkkeSetteNyDatoForKontrollsamtale {
    object FantIkkeSak : KunneIkkeSetteNyDatoForKontrollsamtale
    object UgyldigStatusovergang : KunneIkkeSetteNyDatoForKontrollsamtale
    object FantIkkeGjeldendeStønadsperiode : KunneIkkeSetteNyDatoForKontrollsamtale

    object DatoIkkeFørsteIMåned : KunneIkkeSetteNyDatoForKontrollsamtale
}

sealed interface KunneIkkeKalleInnTilKontrollsamtale {
    object FantIkkeSak : KunneIkkeKalleInnTilKontrollsamtale
    object FantIkkePerson : KunneIkkeKalleInnTilKontrollsamtale
    object KunneIkkeGenerereDokument : KunneIkkeKalleInnTilKontrollsamtale
    object KunneIkkeKalleInn : KunneIkkeKalleInnTilKontrollsamtale
    object FantIkkeGjeldendeStønadsperiode : KunneIkkeKalleInnTilKontrollsamtale
}

sealed interface KunneIkkeHenteKontrollsamtale {
    object FantIkkeKontrollsamtale : KunneIkkeHenteKontrollsamtale
    object KunneIkkeHenteKontrollsamtaler : KunneIkkeHenteKontrollsamtale
}

sealed interface OpprettPlanlagtKontrollsamtaleResultat {
    data class Opprettet(val kontrollsamtale: Kontrollsamtale) : OpprettPlanlagtKontrollsamtaleResultat
    data class PlanlagtKontrollsamtaleFinnesAllerede(val kontrollsamtale: Kontrollsamtale) :
        OpprettPlanlagtKontrollsamtaleResultat

    object SkalIkkePlanleggeKontrollsamtale : OpprettPlanlagtKontrollsamtaleResultat
}

sealed interface AnnulerKontrollsamtaleResultat {
    object IngenKontrollsamtaleÅAnnulere : AnnulerKontrollsamtaleResultat
    data class AnnulertKontrollsamtale(val kontrollsamtale: Kontrollsamtale) : AnnulerKontrollsamtaleResultat
}
