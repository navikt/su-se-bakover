package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KanIkkeTolkeKlagevedtak
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.klage.KlagevedtakUtfall
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak
import no.nav.su.se.bakover.domain.klage.UprosessertKlagevedtak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class KlagevedtakServiceImpl(
    private val klagevedtakRepo: KlagevedtakRepo,
    private val klageRepo: KlageRepo,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val sessionFactory: SessionFactory,
) : KlagevedtakService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(klageVedtak: UprosessertFattetKlagevedtak) {
        klagevedtakRepo.lagre(klageVedtak)
    }

    override fun håndterUtfallFraKlageinstans(deserializeAndMap: (id: UUID, json: String) -> Either<KanIkkeTolkeKlagevedtak, UprosessertKlagevedtak>) {
        val ubehandletKlagevedtak = klagevedtakRepo.hentUbehandlaKlagevedtak()

        ubehandletKlagevedtak.forEach { uprosessertFattetKlagevedtak ->
            deserializeAndMap(uprosessertFattetKlagevedtak.id, uprosessertFattetKlagevedtak.metadata.value)
                .tapLeft {
                    log.error(
                        "Deserializering av melding fra Klageinstans feilet for klagevedtak med hendelseId: ${uprosessertFattetKlagevedtak.metadata.hendelseId}",
                        it,
                    )
                    klagevedtakRepo.markerSomFeil(uprosessertFattetKlagevedtak.id)
                }
                .tap { prosesserKlagevedtak(it) }
        }
    }

    private fun prosesserKlagevedtak(klagevedtak: UprosessertKlagevedtak) {
        val klage = klageRepo.hentKlage(klagevedtak.klageId)

        if (klage == null) {
            log.error("Kunne ikke prosessere melding fra Klageinstans. Fant ikke klage med klageId: ${klagevedtak.klageId}")
            return klagevedtakRepo.markerSomFeil(klagevedtak.id)
        }

        // TODO ai: Flytt over til Klage når vi har ett konsept om vedtak i Klage.
        if (klage !is OversendtKlage) {
            log.error("Kunne ikke prosessere melding fra Klageinstans. Feil skjedde ved uthenting av klagen, forventet ${OversendtKlage::class.java.name} men var ${klage::class.java.name}")
            return klagevedtakRepo.markerSomFeil(klagevedtak.id)
        }

        when (klagevedtak.utfall) {
            KlagevedtakUtfall.STADFESTELSE -> håndterStadfestelse(klagevedtak, klage)
            KlagevedtakUtfall.RETUR -> håndterRetur(klagevedtak, klage)
            KlagevedtakUtfall.TRUKKET,
            KlagevedtakUtfall.OPPHEVET,
            KlagevedtakUtfall.MEDHOLD,
            KlagevedtakUtfall.DELVIS_MEDHOLD,
            KlagevedtakUtfall.UGUNST,
            KlagevedtakUtfall.AVVIST,
            -> {
                /*
                * Desse lagres som FEIL i databasen uten videre håndtering. Tanken er att vi får håndtere
                * de casene som intreffer og så må vi manuellt putte de til 'UPROSSESERT' vid senere tidspunkt.
                * */
                log.error("Utfall: ${klagevedtak.utfall} fra Klageinstans er ikke håndtert.")
                klagevedtakRepo.markerSomFeil(klagevedtak.id)
            }
        }
    }

    private fun håndterRetur(klagevedtak: UprosessertKlagevedtak, klage: OversendtKlage) {
        lagOppgaveConfig(klagevedtak, klage).map { oppgaveConfig ->
            oppgaveService.opprettOppgave(oppgaveConfig).map { oppgaveId ->
                sessionFactory.withTransactionContext { tx ->
                    klageRepo.lagre(klage.copy(oppgaveId = oppgaveId), tx)
                    klagevedtakRepo.lagre(klagevedtak.tilProsessert(oppgaveId), tx)
                }
            }
        }
    }

    private fun håndterStadfestelse(klagevedtak: UprosessertKlagevedtak, klage: OversendtKlage) {
        lagOppgaveConfig(klagevedtak, klage).map { oppgaveConfig ->
            oppgaveService.opprettOppgave(oppgaveConfig).map { oppgaveId ->
                klagevedtakRepo.lagre(klagevedtak.tilProsessert(oppgaveId))
            }
        }
    }

    private fun lagOppgaveConfig(
        klagevedtak: UprosessertKlagevedtak,
        klage: OversendtKlage,
    ): Either<KunneIkkeHenteAktørId, OppgaveConfig.Klage.Saksbehandler> {
        return personService.hentAktørIdMedSystembruker(klage.fnr).map { aktørId ->
            OppgaveConfig.Klage.Saksbehandler(
                saksnummer = klage.saksnummer,
                aktørId = aktørId,
                journalpostId = JournalpostId(klagevedtak.vedtaksbrevReferanse),
                tilordnetRessurs = null,
                clock = Clock.systemUTC(),
            )
        }.mapLeft {
            KunneIkkeHenteAktørId
        }
    }
}

object KunneIkkeHenteAktørId
object KunneIkkeOppretteOppgave
