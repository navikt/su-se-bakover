package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleService {
    fun kallInn(sakId: UUID, saksbehandler: NavIdentBruker): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit>
}

class KontrollsamtaleServiceImpl(
    private val sakService: SakService,
    private val personService: PersonService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : KontrollsamtaleService {

    override fun kallInn(
        sakId: UUID,
        saksbehandler: NavIdentBruker,
    ): Either<KunneIkkeKalleInnTilKontrollsamtale, Unit> {
        val sak = sakService.hentSak(sakId).getOrElse {
            log.error("Fant ikke sak for sakId $sakId")
            return KunneIkkeKalleInnTilKontrollsamtale.FantIkkeSak.left()
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
            sessionFactory.withTransactionContext {
                brevService.lagreDokument(dokument, it)
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
            ifLeft = { KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeKalleInn.left() },
            ifRight = { Unit.right() },
        )
    }

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

sealed class KunneIkkeKalleInnTilKontrollsamtale {
    object FantIkkeSak : KunneIkkeKalleInnTilKontrollsamtale()
    object FantIkkePerson : KunneIkkeKalleInnTilKontrollsamtale()
    object KunneIkkeGenerereDokument : KunneIkkeKalleInnTilKontrollsamtale()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeKalleInnTilKontrollsamtale()
    object KunneIkkeKalleInn : KunneIkkeKalleInnTilKontrollsamtale()
}
