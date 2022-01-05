package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.Klagevedtak
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak
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
) : KlagevedtakService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(klageVedtak: UprosessertFattetKlagevedtak) {
        klagevedtakRepo.lagre(klageVedtak)
    }

    override fun håndterUtfallFraKlageinstans(deserializeAndMap: (id: UUID, json: String) -> Klagevedtak.Uprosessert) {
        val ubehandletKlagevedtak = klagevedtakRepo.hentUbehandlaKlagevedtak()

        ubehandletKlagevedtak.forEach { uprosessertFattetKlagevedtak ->
            Either.catch {
                deserializeAndMap(uprosessertFattetKlagevedtak.id, uprosessertFattetKlagevedtak.metadata.value)
            }.fold(
                ifLeft = {
                    log.error(
                        "Deserializering av melding fra Klageinstans feilet for klagevedtak med hendelseId: ${uprosessertFattetKlagevedtak.metadata.hendelseId}",
                        it,
                    )
                    klagevedtakRepo.markerSomFeil(uprosessertFattetKlagevedtak.id)
                },
                ifRight = { prosesserKlagevedtak(it) },
            )
        }
    }

    private fun prosesserKlagevedtak(klagevedtak: Klagevedtak.Uprosessert) {
        val klage = klageRepo.hentKlage(klagevedtak.klageId)

        if (klage == null) {
            log.error("Kunne ikke prosessere melding fra Klageinstans. Fant ikke klage med klageId: ${klagevedtak.klageId}")
            return klagevedtakRepo.markerSomFeil(klagevedtak.id)
        }

        if (klage !is OversendtKlage) {
            log.error("Kunne ikke prosessere melding fra Klageinstans. Feil skjedde ved uthenting av klagen, forventet ${OversendtKlage::class.java.name} men var ${klage::class.java.name}")
            return klagevedtakRepo.markerSomFeil(klagevedtak.id)
        }

        when (klagevedtak.utfall) {
            Klagevedtak.Utfall.STADFESTELSE -> håndterStadfestelse(klagevedtak, klage)
            Klagevedtak.Utfall.RETUR -> håndterRetur(klagevedtak, klage)
            Klagevedtak.Utfall.TRUKKET,
            Klagevedtak.Utfall.OPPHEVET,
            Klagevedtak.Utfall.MEDHOLD,
            Klagevedtak.Utfall.DELVIS_MEDHOLD,
            Klagevedtak.Utfall.UGUNST,
            Klagevedtak.Utfall.AVVIST,
            -> {
                log.error("Utfall: ${klagevedtak.utfall} fra Klageinstans er ikke håndtert.")
                klagevedtakRepo.markerSomFeil(klagevedtak.id)
            }
        }
    }

    private fun håndterRetur(klagevedtak: Klagevedtak.Uprosessert, klage: OversendtKlage) {
        lagOppgaveConfig(klagevedtak, klage).map { oppgaveConfig ->
            oppgaveService.opprettOppgave(oppgaveConfig).map { oppgaveId ->
                klageRepo.lagre(klage.copy(oppgaveId = oppgaveId))
                klagevedtakRepo.lagreProsessertKlagevedtak(klagevedtak.tilProsessert(oppgaveId))
            }
        }
    }

    private fun håndterStadfestelse(klagevedtak: Klagevedtak.Uprosessert, klage: OversendtKlage) {
        lagOppgaveConfig(klagevedtak, klage).map { oppgaveConfig ->
            oppgaveService.opprettOppgave(oppgaveConfig).map { oppgaveId ->
                klagevedtakRepo.lagreProsessertKlagevedtak(klagevedtak.tilProsessert(oppgaveId))
            }
        }
    }

    private fun lagOppgaveConfig(
        klagevedtak: Klagevedtak.Uprosessert,
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
