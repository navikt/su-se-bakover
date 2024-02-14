package no.nav.su.se.bakover.vedtak.application

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNysøknadsbehandlingMedNyOppgaveId
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.InnvilgetForMåned
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.Vedtaksammendrag
import no.nav.su.se.bakover.domain.vedtak.tilInnvilgetForMåned
import no.nav.su.se.bakover.vedtak.domain.KunneIkkeStarteNySøknadsbehandling
import no.nav.su.se.bakover.vedtak.domain.Vedtak
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class VedtakServiceImpl(
    private val vedtakRepo: VedtakRepo,
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val personservice: PersonService,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val clock: Clock,
) : VedtakService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(vedtak: Vedtak) {
        return vedtakRepo.lagre(vedtak)
    }

    override fun lagreITransaksjon(vedtak: Vedtak, tx: TransactionContext) {
        return vedtakRepo.lagreITransaksjon(vedtak, tx)
    }

    override fun hentForVedtakId(vedtakId: UUID): Vedtak? {
        return vedtakRepo.hentVedtakForId(vedtakId)
    }

    override fun hentForRevurderingId(revurderingId: RevurderingId): Vedtak? {
        return vedtakRepo.hentForRevurderingId(revurderingId)
    }

    override fun hentJournalpostId(vedtakId: UUID): JournalpostId? {
        return vedtakRepo.hentJournalpostId(vedtakId)
    }

    override fun hentInnvilgetFnrForMåned(måned: Måned): InnvilgetForMåned {
        return vedtakRepo.hentForMåned(måned).tilInnvilgetForMåned(måned)
    }

    override fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes? {
        return vedtakRepo.hentForUtbetaling(utbetalingId)
    }

    override fun hentForFødselsnumreOgFraOgMedMåned(fødselsnumre: List<Fnr>, fraOgMed: Måned): List<Vedtaksammendrag> {
        return vedtakRepo.hentForFødselsnumreOgFraOgMedMåned(fødselsnumre, fraOgMed)
    }

    override fun hentSøknadsbehandlingsvedtakFraOgMed(fraOgMed: LocalDate): List<UUID> {
        return vedtakRepo.hentSøknadsbehandlingsvedtakFraOgMed(fraOgMed)
    }

    override fun startNySøknadsbehandlingForAvslag(
        sakId: UUID,
        vedtakId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeStarteNySøknadsbehandling, Søknadsbehandling> {
        val sak = sakService.hentSak(sakId).getOrElse {
            return KunneIkkeStarteNySøknadsbehandling.FantIkkeSak.left()
        }

        val vedtak = sak.vedtakListe.find { it.id == vedtakId }.let {
            it ?: return KunneIkkeStarteNySøknadsbehandling.FantIkkeVedtak.left()
            it as? Avslagsvedtak ?: return KunneIkkeStarteNySøknadsbehandling.VedtakErIkkeAvslag.left()
        }

        val aktørId = personservice.hentAktørId(vedtak.fnr).getOrElse {
            log.error("Feil ved henting av aktør id for opprettelse av oppgave for ny søknadsbehandling for vedtak $vedtakId. original feil $it")
            return KunneIkkeStarteNySøknadsbehandling.FeilVedHentingAvPersonForOpprettelseAvOppgave(it).left()
        }

        return sak.opprettNysøknadsbehandlingMedNyOppgaveId(
            søknadId = vedtak.behandling.søknad.id,
            clock = clock,
            saksbehandler = saksbehandler,
            opprettOppgave = {
                oppgaveService.opprettOppgave(
                    OppgaveConfig.Søknad(
                        journalpostId = vedtak.behandling.søknad.journalpostId,
                        søknadId = vedtak.behandling.søknad.id,
                        aktørId = aktørId,
                        tilordnetRessurs = saksbehandler,
                        clock = clock,
                        sakstype = sak.type,
                    ),
                )
            },
        ).map {
            søknadsbehandlingService.lagre(it.second)
            it.second
        }.mapLeft {
            KunneIkkeStarteNySøknadsbehandling.FeilVedOpprettelseAvSøknadsbehandling(it)
        }
    }
}
