package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KanIkkeTolkeKlagevedtak
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.klage.KlagevedtakUtfall
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlageinstansvedtak
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstansvedtak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
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
    private val clock: Clock,
) : KlagevedtakService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(klageVedtak: UprosessertFattetKlageinstansvedtak) {
        klagevedtakRepo.lagre(klageVedtak)
    }

    override fun håndterUtfallFraKlageinstans(deserializeAndMap: (id: UUID, opprettet: Tidspunkt, json: String) -> Either<KanIkkeTolkeKlagevedtak, UprosessertKlageinstansvedtak>) {
        val ubehandletKlagevedtak = klagevedtakRepo.hentUbehandlaKlagevedtak()

        ubehandletKlagevedtak.forEach { uprosessertFattetKlagevedtak ->
            deserializeAndMap(
                uprosessertFattetKlagevedtak.id,
                uprosessertFattetKlagevedtak.opprettet,
                uprosessertFattetKlagevedtak.metadata.value,
            )
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

    private fun prosesserKlagevedtak(klagevedtak: UprosessertKlageinstansvedtak) {
        val klage = klageRepo.hentKlage(klagevedtak.klageId)

        if (klage == null) {
            log.error("Kunne ikke prosessere melding fra Klageinstans. Fant ikke klage med klageId: ${klagevedtak.klageId}")
            return klagevedtakRepo.markerSomFeil(klagevedtak.id)
        }

        klage.leggTilNyttKlagevedtak(
            klagevedtak,
        ) {
            lagOppgaveConfig(
                klage.saksnummer,
                klage.fnr,
                klagevedtak.utfall,
                JournalpostId(klagevedtak.vedtaksbrevReferanse),
            )
        }.tapLeft {
            when (it) {
                Klage.KunneIkkeLeggeTilNyttKlageinstansVedtak.IkkeStøttetUtfall -> log.error("Utfall: ${klagevedtak.utfall} fra Klageinstans er ikke håndtert.")
                Klage.KunneIkkeLeggeTilNyttKlageinstansVedtak.KunneIkkeHenteAktørId -> log.error("Feil skjedde i prosessering av vedtak fra Klageinstans. Kunne ikke hente aktørId for klagevedtak: ${klagevedtak.id}")
                is Klage.KunneIkkeLeggeTilNyttKlageinstansVedtak.KunneIkkeLageOppgave -> log.error("Feil skjedde i prosessering av vedtak fra Klageinstans. Kall mot oppgave feilet for klagevedtak: ${klagevedtak.id}")
                is Klage.KunneIkkeLeggeTilNyttKlageinstansVedtak.MåVæreEnOversendtKlage -> log.error("Feil skjedde i prosessering av vedtak fra Klageinstans. Må være i tilstand ${OversendtKlage::class.java.name} men var ${it.menVar.java.name} for klagevedtak: ${klagevedtak.id}")
            }
            /** Disse lagres som FEIL i databasen uten videre håndtering. Tanken er at vi får håndtere
             * de casene som intreffer og så må vi manuellt putte de til 'UPROSSESERT' vid senere tidspunkt.
             */
            klagevedtakRepo.markerSomFeil(klagevedtak.id)
        }
            .tap {
                sessionFactory.withTransactionContext { tx ->
                    klageRepo.lagre(it, tx)
                    klagevedtakRepo.lagre(it.klagevedtakshistorikk.last(), tx)
                }
                log.info("Prosessert og lagat oppgave for klageinstansvedtak med id: ${klagevedtak.id}")
            }
    }

    private fun lagOppgaveConfig(
        saksnummer: Saksnummer,
        fnr: Fnr,
        utfall: KlagevedtakUtfall,
        journalpostId: JournalpostId,
    ): Either<Klage.KunneIkkeLeggeTilNyttKlageinstansVedtak, OppgaveId> {
        return personService.hentAktørIdMedSystembruker(fnr).map { aktørId ->
            when (utfall) {
                KlagevedtakUtfall.TRUKKET,
                KlagevedtakUtfall.AVVIST,
                KlagevedtakUtfall.STADFESTELSE,
                -> OppgaveConfig.Klage.Vedtak.Informasjon(
                    saksnummer = saksnummer,
                    aktørId = aktørId,
                    journalpostId = journalpostId,
                    tilordnetRessurs = null,
                    clock = clock,
                    utfall = utfall,
                )
                KlagevedtakUtfall.RETUR,
                KlagevedtakUtfall.OPPHEVET,
                KlagevedtakUtfall.MEDHOLD,
                KlagevedtakUtfall.DELVIS_MEDHOLD,
                KlagevedtakUtfall.UGUNST,
                -> OppgaveConfig.Klage.Vedtak.Handling(
                    saksnummer = saksnummer,
                    aktørId = aktørId,
                    journalpostId = journalpostId,
                    tilordnetRessurs = null,
                    clock = clock,
                    utfall = utfall,
                )
            }
        }.mapLeft { Klage.KunneIkkeLeggeTilNyttKlageinstansVedtak.KunneIkkeHenteAktørId }
            .flatMap {
                oppgaveService.opprettOppgaveMedSystembruker(it)
                    .mapLeft { feil -> Klage.KunneIkkeLeggeTilNyttKlageinstansVedtak.KunneIkkeLageOppgave(feil) }
            }
    }
}
