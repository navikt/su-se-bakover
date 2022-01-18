package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleService {
    fun kallInn(sakId: UUID, kontrollsamtale: Kontrollsamtale): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit>
    fun nyDato(sakId: UUID, dato: LocalDate): Either<KunneIkkeSetteNyDatoForKontrollsamtale, Unit>
    fun hentNestePlanlagteKontrollsamtale(sakId: UUID): Either<KunneIkkeHenteKontrollsamtale, Kontrollsamtale>
    fun hentPlanlagteKontrollsamtaler(): Either<KunneIkkeHenteKontrollsamtale, List<Kontrollsamtale>>
    fun opprettPlanlagtKontrollsamtale(vedtak: Vedtak): Either<KunneIkkeKalleInnTilKontrollsamtale, Kontrollsamtale>
    fun oppdaterNestePlanlagteKontrollsamtaleStatus(
        sakId: UUID,
        status: Kontrollsamtalestatus,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit>
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

        val person = personService.hentPerson(sak.fnr).getOrElse {
            log.error("Fant ikke person for fnr: ${sak.fnr}")
            return KunneIkkeKalleInnTilKontrollsamtale.FantIkkePerson.left()
        }

        val dokument = lagDokument(person, sakId).getOrElse {
            log.error("Klarte ikke lage dokument for innkalling til kontrollsamtale på sakId $sakId")
            return KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeGenerereDokument.left()
        }

        return Either.catch {
            sessionFactory.withTransactionContext { tx ->
                brevService.lagreDokument(dokument, tx)
                kontrollsamtaleRepo.lagre(
                    kontrollsamtale = kontrollsamtale.oppdater(
                        status = Kontrollsamtalestatus.INNKALT,
                        dokumentId = dokument.id,
                    ),
                    transactionContext = tx,
                )
                Kontrollsamtale.opprettNyKontrollsamtale(gjeldendeStønadsperiode, sakId, clock).map {
                    lagreInnkallingTilKontrollsamtale(it, tx).getOrElse {
                        throw RuntimeException("Fikk ikke opprettet ny innkalling til neste kontrollsamtale")
                    }
                }
                oppgaveService.opprettOppgave(
                    config = OppgaveConfig.Kontrollsamtale(
                        saksnummer = sak.saksnummer,
                        aktørId = person.ident.aktørId,
                        clock = clock,
                    ),
                )
                    .getOrElse {
                        throw RuntimeException("Fikk ikke opprettet oppgave").also {
                            log.error("Fikk ikke opprettet oppgave")
                        }
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

    override fun nyDato(sakId: UUID, dato: LocalDate): Either<KunneIkkeSetteNyDatoForKontrollsamtale, Unit> =
        hentNestePlanlagteKontrollsamtale(sakId).fold(
            ifLeft = {
                val nyKontrollsamtale = Kontrollsamtale(
                    sakId = sakId,
                    innkallingsdato = dato,
                    status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
                    dokumentId = null,
                )
                kontrollsamtaleRepo.lagre(nyKontrollsamtale)
            },
            ifRight = {
                kontrollsamtaleRepo.lagre(it.oppdater(innkallingsdato = dato))
            },
        ).right()

    override fun hentNestePlanlagteKontrollsamtale(sakId: UUID): Either<KunneIkkeHenteKontrollsamtale, Kontrollsamtale> {
        val samtaler = kontrollsamtaleRepo.hentForSakId(sakId).sortedBy { it.innkallingsdato }
        return samtaler.find { it.status === Kontrollsamtalestatus.PLANLAGT_INNKALLING }?.right()
            ?: KunneIkkeHenteKontrollsamtale.FantIkkeKontrollsamtale.left()
    }

    override fun hentPlanlagteKontrollsamtaler(): Either<KunneIkkeHenteKontrollsamtale, List<Kontrollsamtale>> =
        Either.catch { kontrollsamtaleRepo.hentAllePlanlagte(LocalDate.now(clock)) }.mapLeft {
            log.error("Kunne ikke hente planlagte kontrollsamtaler før ${LocalDate.now(clock)}", it)
            return KunneIkkeHenteKontrollsamtale.KunneIkkeHenteKontrollsamtaler.left()
        }

    override fun opprettPlanlagtKontrollsamtale(vedtak: Vedtak): Either<KunneIkkeKalleInnTilKontrollsamtale, Kontrollsamtale> {
        val planlagtKontrollsamtaleEksisterer = hentNestePlanlagteKontrollsamtale(vedtak.behandling.sakId).isRight()

        return if (planlagtKontrollsamtaleEksisterer) {
            KunneIkkeKalleInnTilKontrollsamtale.PlanlagtKontrollsamtaleFinnesAllerede.left()
        } else
            Kontrollsamtale.opprettNyKontrollsamtaleFraVedtak(vedtak, clock).flatMap { kontrollsamtale ->
                sessionFactory.withTransactionContext {
                    lagreInnkallingTilKontrollsamtale(kontrollsamtale, it)
                }
            }.mapLeft {
                KunneIkkeKalleInnTilKontrollsamtale.SkalIkkePlanleggeKontrollsamtale
            }
    }

    override fun oppdaterNestePlanlagteKontrollsamtaleStatus(
        sakId: UUID,
        status: Kontrollsamtalestatus,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit> =
        hentNestePlanlagteKontrollsamtale(sakId).mapLeft {
            log.info("Fant ingen planlagt kontrollsamtale for sakId $sakId")
            KunneIkkeKalleInnTilKontrollsamtale.FantIkkeKontrollsamtale
        }.map {
            kontrollsamtaleRepo.lagre(it.oppdater(status = status))
        }

    private fun lagreInnkallingTilKontrollsamtale(
        kontrollsamtale: Kontrollsamtale,
        transactionContext: TransactionContext,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, Kontrollsamtale> =
        Either.catch {
            kontrollsamtaleRepo.lagre(kontrollsamtale, transactionContext)
        }.fold(
            ifLeft = {
                log.error("Fikk ikke opprettet inkalling til ny kontrollsamtale for sakId ${kontrollsamtale.sakId}")
                KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeLagreKontrollsamtale.left()
            },
            ifRight = {
                log.info("Opprettet ny kontrollsamtale for sakId ${kontrollsamtale.sakId}")
                kontrollsamtale.right()
            },
        )

    private fun lagDokument(
        person: Person,
        sakId: UUID,
    ): Either<KunneIkkeLageBrev, Dokument.MedMetadata.Informasjon> {
        val brevRequest = LagBrevRequest.InnkallingTilKontrollsamtale(
            person = person,
            dagensDato = LocalDate.now(clock),
        )
        return brevService.lagBrev(brevRequest).map {
            Dokument.UtenMetadata.Informasjon(
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
}

sealed interface KunneIkkeSetteNyDatoForKontrollsamtale {
    object KunneIkkeEndreDato : KunneIkkeSetteNyDatoForKontrollsamtale
}

sealed interface KunneIkkeKalleInnTilKontrollsamtale {
    object FantIkkeSak : KunneIkkeKalleInnTilKontrollsamtale
    object FantIkkePerson : KunneIkkeKalleInnTilKontrollsamtale
    object KunneIkkeGenerereDokument : KunneIkkeKalleInnTilKontrollsamtale
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeKalleInnTilKontrollsamtale
    object KunneIkkeKalleInn : KunneIkkeKalleInnTilKontrollsamtale
    object FantIkkeGjeldendeStønadsperiode : KunneIkkeKalleInnTilKontrollsamtale
    object KunneIkkeLagreKontrollsamtale : KunneIkkeKalleInnTilKontrollsamtale
    object FantIkkeKontrollsamtale : KunneIkkeKalleInnTilKontrollsamtale
    object SkalIkkePlanleggeKontrollsamtale : KunneIkkeKalleInnTilKontrollsamtale
    object PlanlagtKontrollsamtaleFinnesAllerede : KunneIkkeKalleInnTilKontrollsamtale
}

sealed interface KunneIkkeHenteKontrollsamtale {
    object FantIkkeKontrollsamtale : KunneIkkeHenteKontrollsamtale
    object KunneIkkeHenteKontrollsamtaler : KunneIkkeHenteKontrollsamtale
}
