package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import no.nav.su.se.bakover.domain.kontrollsamtale.regnUtFristFraInnkallingsdato
import no.nav.su.se.bakover.domain.kontrollsamtale.regnUtInnkallingsdato
import no.nav.su.se.bakover.domain.kontrollsamtale.regnUtInnkallingsdatoOm4Mnd
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
    fun hentPlanlagteKontrollsamtaler(clock: Clock): Either<KunneIkkeHenteKontrollsamtale, List<Kontrollsamtale>>
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
                opprettPlanlagtKontrollsamtaleOmDetTrengs(
                    gjeldendeStønadsperiode = gjeldendeStønadsperiode,
                    sakId = sakId,
                    tx = tx,
                )
                kontrollsamtaleRepo.oppdaterKontrollsamtale(
                    kontrollsamtale = kontrollsamtale.copy(
                        status = Kontrollsamtalestatus.INNKALT,
                        dokumentId = dokument.id,
                    ),
                    transactionContext = tx,
                )
                oppgaveService.opprettOppgave(
                    config = OppgaveConfig.Kontrollsamtale(
                        saksnummer = sak.saksnummer,
                        aktørId = person.ident.aktørId,
                        clock = clock,
                    )
                )
                    .getOrElse {
                        throw RuntimeException("Fikk ikke opprettet oppgave").also {
                            log.error("Fikk ikke opprettet oppgave")
                        }
                    }
            }
        }.fold(
            ifLeft = {
                log.error("Klarte ikke kalle inn kontrollsamtale for sakId $sakId")
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
                    frist = regnUtFristFraInnkallingsdato(dato),
                    dokumentId = null,
                )
                kontrollsamtaleRepo.lagre(nyKontrollsamtale)
            },
            ifRight = {
                kontrollsamtaleRepo.oppdaterKontrollsamtale(
                    it.copy(
                        innkallingsdato = dato,
                        frist = regnUtFristFraInnkallingsdato(dato),
                    ),
                )
            },
        ).right()

    override fun hentNestePlanlagteKontrollsamtale(sakId: UUID): Either<KunneIkkeHenteKontrollsamtale, Kontrollsamtale> {
        val samtaler = kontrollsamtaleRepo.hent(sakId).sortedBy { it.innkallingsdato }
        return samtaler.find { it.status === Kontrollsamtalestatus.PLANLAGT_INNKALLING }?.right()
            ?: KunneIkkeHenteKontrollsamtale.FantIkkeKontrollsamtale.left()
    }

    override fun hentPlanlagteKontrollsamtaler(clock: Clock): Either<KunneIkkeHenteKontrollsamtale, List<Kontrollsamtale>> =
        Either.catch { kontrollsamtaleRepo.hentAllePlanlagte(LocalDate.now(clock)) }.mapLeft {
            log.error("Kunne ikke hente planlagte kontrollsamtaler før ${LocalDate.now(clock)}. Stacktrace: ${it.stackTrace}")
            return KunneIkkeHenteKontrollsamtale.KunneIkkeHenteKontrollsamtaler.left()
        }

    override fun opprettPlanlagtKontrollsamtale(vedtak: Vedtak): Either<KunneIkkeKalleInnTilKontrollsamtale, Kontrollsamtale> {
        val innkallingsdato = regnUtInnkallingsdato(vedtak.periode, vedtak.opprettet.toLocalDate(zoneIdOslo), clock)
            ?: return KunneIkkeKalleInnTilKontrollsamtale.SkalIkkePlanleggeKontrollsamtale.left()

        val kontrollsamtale = Kontrollsamtale(
            sakId = vedtak.behandling.sakId,
            innkallingsdato = innkallingsdato,
            status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
            frist = regnUtFristFraInnkallingsdato(innkallingsdato),
            dokumentId = null,
        )

        val planlagtKontrollsamtaleEksisterer = hentNestePlanlagteKontrollsamtale(vedtak.behandling.sakId).isRight()

        return if (planlagtKontrollsamtaleEksisterer) {
            KunneIkkeKalleInnTilKontrollsamtale.PlanlagtKontrollsamtaleFinnesAllerede.left()
        } else {
            sessionFactory.withTransactionContext {
                lagreInnkallingTilKontrollsamtale(kontrollsamtale, it)
            }
            kontrollsamtale.right()
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
            kontrollsamtaleRepo.oppdaterStatus(it.id, status)
        }

    private fun opprettPlanlagtKontrollsamtaleOmDetTrengs(
        gjeldendeStønadsperiode: Periode,
        sakId: UUID,
        dokumentId: UUID? = null,
        tx: TransactionContext,
    ) {
        val innkallingsdato =
            regnUtInnkallingsdatoOm4Mnd(gjeldendeStønadsperiode.tilOgMed, LocalDate.now(clock)) ?: return

        val kontrollsamtale = Kontrollsamtale(
            sakId = sakId,
            innkallingsdato = innkallingsdato,
            status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
            frist = regnUtFristFraInnkallingsdato(innkallingsdato),
            dokumentId = dokumentId,
        )
        lagreInnkallingTilKontrollsamtale(kontrollsamtale, tx).getOrElse {
            throw RuntimeException("Fikk ikke opprettet innkalling til ny kontrollsamtale")
        }
    }

    private fun lagreInnkallingTilKontrollsamtale(
        kontrollsamtale: Kontrollsamtale,
        transactionContext: TransactionContext,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, LagreKontrollsamtale> =
        Either.catch {
            kontrollsamtaleRepo.lagre(kontrollsamtale, transactionContext)
        }.fold(
            ifLeft = {
                log.error("Fikk ikke opprettet inkalling til ny kontrollsamtale for sakId ${kontrollsamtale.sakId}")
                KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeLagreKontrollsamtale.left()
            },
            ifRight = {
                log.info("Opprettet ny kontrollsamtale for sakId ${kontrollsamtale.sakId}")
                LagreKontrollsamtale.InnkallingOpprettet.right()
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

sealed class KunneIkkeSetteNyDatoForKontrollsamtale {
    object KunneIkkeEndreDato : KunneIkkeSetteNyDatoForKontrollsamtale()
}

sealed class KunneIkkeKalleInnTilKontrollsamtale {
    object FantIkkeSak : KunneIkkeKalleInnTilKontrollsamtale()
    object FantIkkePerson : KunneIkkeKalleInnTilKontrollsamtale()
    object KunneIkkeGenerereDokument : KunneIkkeKalleInnTilKontrollsamtale()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeKalleInnTilKontrollsamtale()
    object KunneIkkeKalleInn : KunneIkkeKalleInnTilKontrollsamtale()
    object FantIkkeGjeldendeStønadsperiode : KunneIkkeKalleInnTilKontrollsamtale()
    object KunneIkkeLagreKontrollsamtale : KunneIkkeKalleInnTilKontrollsamtale()
    object FantIkkeKontrollsamtale : KunneIkkeKalleInnTilKontrollsamtale()
    object SkalIkkePlanleggeKontrollsamtale : KunneIkkeKalleInnTilKontrollsamtale()
    object PlanlagtKontrollsamtaleFinnesAllerede : KunneIkkeKalleInnTilKontrollsamtale()
}

sealed class LagreKontrollsamtale {
    object InnkallingOpprettet : LagreKontrollsamtale()
}

sealed class KunneIkkeHenteKontrollsamtale {
    object FantIkkeKontrollsamtale : KunneIkkeHenteKontrollsamtale()
    object KunneIkkeHenteKontrollsamtaler : KunneIkkeHenteKontrollsamtale()
}
