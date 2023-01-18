package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.AlleredeGjeldendeSakForBruker
import no.nav.su.se.bakover.domain.BegrensetSakinfo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.sak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak
import no.nav.su.se.bakover.domain.sak.KunneIkkeHenteGjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.sak.KunneIkkeOppretteDokument
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.OpprettDokumentRequest
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class SakServiceImpl(
    private val sakRepo: SakRepo,
    private val clock: Clock,
    private val dokumentRepo: DokumentRepo,
    private val brevService: BrevService,
    private val personService: PersonService,
    private val identClient: IdentClient,
) : SakService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<StatistikkEventObserver> = observers.toList()

    override fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(sakId)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSak(sakId: UUID, sessionContext: SessionContext): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(sakId, sessionContext)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSak(fnr: Fnr, type: Sakstype): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(fnr, type)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSaker(fnr: Fnr): Either<FantIkkeSak, List<Sak>> {
        val saker = sakRepo.hentSaker(fnr)
        if (saker.isEmpty()) {
            return FantIkkeSak.left()
        }
        return saker.right()
    }

    override fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(saksnummer)?.right() ?: FantIkkeSak.left()
    }

    override fun hentGjeldendeVedtaksdata(
        sakId: UUID,
        periode: Periode,
    ): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata?> {
        return hentSak(sakId).mapLeft { KunneIkkeHenteGjeldendeVedtaksdata.FantIkkeSak }.flatMap { sak ->
            sak.hentGjeldendeVedtaksdata(periode, clock).mapLeft { KunneIkkeHenteGjeldendeVedtaksdata.IngenVedtak }
        }
    }

    override fun historiskGrunnlagForVedtaketsPeriode(
        sakId: UUID,
        vedtakId: UUID,
    ): Either<KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak, GjeldendeVedtaksdata> {
        return sakRepo.hentSak(sakId)
            ?.historiskGrunnlagForVedtaketsPeriode(
                vedtakId = vedtakId,
                clock = clock,
            )?.mapLeft {
                KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.Feil(it)
            }
            ?: KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.FantIkkeSak.left()
    }

    override fun hentSakidOgSaksnummer(fnr: Fnr): Either<FantIkkeSak, SakInfo> {
        return sakRepo.hentSakInfoForIdenter(personidenter = nonEmptyListOf(fnr.toString()))?.right()
            ?: FantIkkeSak.left()
    }

    override fun hentSakInfo(sakId: UUID): Either<FantIkkeSak, SakInfo> {
        return sakRepo.hentSakInfo(sakId)?.right()
            ?: FantIkkeSak.left()
    }

    override fun hentSakForRevurdering(revurderingId: UUID): Sak {
        return sakRepo.hentSakForRevurdering(revurderingId)
    }

    override fun hentSakForRevurdering(revurderingId: UUID, sessionContext: SessionContext): Sak {
        return sakRepo.hentSakForRevurdering(revurderingId, sessionContext)
    }

    override fun hentSakForSøknadsbehandling(søknadsbehandlingId: UUID): Sak {
        return sakRepo.hentSakforSøknadsbehandling(søknadsbehandlingId)
    }

    override fun hentSakForSøknad(søknadId: UUID): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSakForSøknad(søknadId)?.right() ?: FantIkkeSak.left()
    }

    override fun opprettFritekstDokument(request: OpprettDokumentRequest): Either<KunneIkkeOppretteDokument, Dokument.UtenMetadata> {
        val sak = sakRepo.hentSak(request.sakId)
            ?: throw IllegalStateException("Fant ikke sak ved opprettFritekstDokument. sakid ${request.sakId}")

        val person = personService.hentPerson(sak.fnr).getOrElse {
            throw IllegalStateException("Fant ikke person ved opprettFritekstDokument. sakid ${request.sakId}, fnr ${sak.fnr}")
        }

        val saksbehandlerNavn = identClient.hentNavnForNavIdent(request.saksbehandler).getOrElse {
            return KunneIkkeOppretteDokument.FeilVedHentingAvSaksbehandlernavn(it).left()
        }

        return brevService.lagDokument(
            LagBrevRequest.Fritekst(
                person = person,
                dagensDato = LocalDate.now(clock),
                saksnummer = sak.saksnummer,
                saksbehandlerNavn = saksbehandlerNavn,
                brevTittel = request.tittel,
                fritekst = request.fritekst,
            ),
        ).mapLeft {
            KunneIkkeOppretteDokument.KunneIkkeLageDokument(it)
        }
    }

    override fun lagreOgSendFritekstDokument(request: OpprettDokumentRequest): Either<KunneIkkeOppretteDokument, Dokument.MedMetadata> {
        return opprettFritekstDokument(request).map {
            it.leggTilMetadata(
                Dokument.Metadata(
                    sakId = request.sakId,
                    bestillBrev = true,
                ),
            )
        }.onRight {
            dokumentRepo.lagre(it)
        }
    }

    override fun opprettSak(sak: NySak) {
        sakRepo.opprettSak(sak).also {
            hentSak(sak.id).fold(
                ifLeft = { log.error("Opprettet sak men feilet ved henting av den.") },
                ifRight = {
                    observers.forEach { observer -> observer.handle(StatistikkEvent.SakOpprettet(it)) }
                },
            )
        }
    }

    override fun hentÅpneBehandlingerForAlleSaker(): List<Behandlingsoversikt> {
        return sakRepo.hentÅpneBehandlinger()
    }

    override fun hentFerdigeBehandlingerForAlleSaker(): List<Behandlingsoversikt> {
        return sakRepo.hentFerdigeBehandlinger()
    }

    override fun hentAlleredeGjeldendeSakForBruker(fnr: Fnr): AlleredeGjeldendeSakForBruker {
        return hentSaker(fnr).fold(
            ifLeft = {
                AlleredeGjeldendeSakForBruker(
                    sakTilBegrensetSakInfo(null),
                    sakTilBegrensetSakInfo(null),
                )
            },
            ifRight = { saker ->
                AlleredeGjeldendeSakForBruker(
                    uføre = sakTilBegrensetSakInfo(saker.find { it.type == Sakstype.UFØRE }),
                    alder = sakTilBegrensetSakInfo(saker.find { it.type == Sakstype.ALDER }),
                )
            },
        )
    }

    private fun sakTilBegrensetSakInfo(sak: Sak?): BegrensetSakinfo {
        if (sak == null) {
            return BegrensetSakinfo(false, null)
        }
        return BegrensetSakinfo(
            harÅpenSøknad = sak.søknader.any { søknad ->
                val behandling = sak.søknadsbehandlinger.find { b -> b.søknad.id == søknad.id }
                (søknad !is Søknad.Journalført.MedOppgave.Lukket && (behandling == null || !behandling.erIverksatt))
            },
            iverksattInnvilgetStønadsperiode = sak.hentGjeldendeStønadsperiode(clock),
        )
    }
}
