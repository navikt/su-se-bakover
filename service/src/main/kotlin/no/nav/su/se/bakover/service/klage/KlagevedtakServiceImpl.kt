package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.FattetKlagevedtak
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class KlagevedtakServiceImpl(
    private val klagevedtakRepo: KlagevedtakRepo,
    private val oppgaveService: OppgaveService,
) : KlagevedtakService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(klageVedtak: UprosessertFattetKlagevedtak) {
        klagevedtakRepo.lagre(klageVedtak)
    }

    /*
    * TODO: Legg på mekanism for å håndtere når det feiler på deserializering?
    * */
    override fun håndterUtfallFraKlageinstans() {
        val ubehandletKlagevedtak = klagevedtakRepo.hentUbehandlaKlagevedtak()

        ubehandletKlagevedtak.mapNotNull { uprosessertFattetKlagevedtak ->
            Either.catch {
                deserialize<FattetKlagevedtak>(uprosessertFattetKlagevedtak.metadata.value)
            }.fold(
                ifLeft = {
                    log.error("Deserializering av melding fra Klageinstans feilet for klagevedtak med hendelseId: ${uprosessertFattetKlagevedtak.metadata.hendelseId}", it)
                    klagevedtakRepo.markerSomFeil(uprosessertFattetKlagevedtak.id)
                    null
                },
                ifRight = { klagevedtak -> Pair(uprosessertFattetKlagevedtak.id, klagevedtak) },
            )
        }.map { (id, klagevedtak) ->
            when (klagevedtak.utfall) {
                "STADFESTELSE" -> håndterStadfestelse(id)
                "RETUR" -> håndterRetur(id, klagevedtak)
                else -> {
                    log.error("Utfall: ${klagevedtak.utfall} fra Klageinstans er ikke håndtert.")
                    klagevedtakRepo.markerSomFeil(id)
                }
            }
        }
    }

    private fun håndterRetur(id: UUID, klagevedtak: FattetKlagevedtak) {
        /* TODO: Hent ut riktig data */
        val oppgaveConfig: OppgaveConfig = OppgaveConfig.Klage.Saksbehandler(
            saksnummer = Saksnummer(nummer = 0),
            aktørId = AktørId(aktørId = ""),
            journalpostId = JournalpostId(klagevedtak.vedtaksbrevReferanse!!),
            tilordnetRessurs = null,
            clock = Clock.systemUTC(),
        )

        oppgaveService.opprettOppgave(oppgaveConfig)
            .map { klagevedtakRepo.lagreOppgaveIdOgMarkerSomProssesert(id, it) }
    }

    private fun håndterStadfestelse(id: UUID) =
        klagevedtakRepo.markerSomProssesert(id)
}
