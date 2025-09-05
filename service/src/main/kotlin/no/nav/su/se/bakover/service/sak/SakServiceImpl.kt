package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.brev.BrevService
import dokument.domain.journalføring.Journalpost
import dokument.domain.journalføring.KunneIkkeHenteJournalposter
import dokument.domain.journalføring.QueryJournalpostClient
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.singleOrNullOrThrow
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.AlleredeGjeldendeSakForBruker
import no.nav.su.se.bakover.domain.BegrensetSakinfo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.sak.JournalførOgSendOpplastetPdfSomBrevCommand
import no.nav.su.se.bakover.domain.sak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak
import no.nav.su.se.bakover.domain.sak.KunneIkkeHenteGjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.sak.KunneIkkeOppretteDokument
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.OpprettDokumentRequest
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.fnr.KunneIkkeOppdatereFødselsnummer
import no.nav.su.se.bakover.domain.sak.fnr.OppdaterFødselsnummerPåSakCommand
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class SakServiceImpl(
    private val sakRepo: SakRepo,
    private val clock: Clock,
    private val dokumentRepo: DokumentRepo,
    private val brevService: BrevService,
    private val journalpostClient: QueryJournalpostClient,
    private val personService: PersonService,
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

    override fun hentSakForUtbetalingId(utbetalingId: UUID30): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSakForUtbetalingId(utbetalingId)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSak(hendelseId: HendelseId): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(hendelseId)?.right() ?: FantIkkeSak.left()
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

    override fun hentSakidOgSaksnummer(fnr: Fnr, sakstype: Sakstype): SakInfo? {
        return sakRepo.hentSakInfoForIdent(fnr, sakstype)
    }

    override fun hentSakInfo(sakId: UUID): Either<FantIkkeSak, SakInfo> {
        return sakRepo.hentSakInfo(sakId)?.right()
            ?: FantIkkeSak.left()
    }

    override fun hentSakForRevurdering(revurderingId: RevurderingId): Sak {
        return sakRepo.hentSakForRevurdering(revurderingId)
    }

    override fun hentSakForRevurdering(revurderingId: RevurderingId, sessionContext: SessionContext): Sak {
        return sakRepo.hentSakForRevurdering(revurderingId, sessionContext)
    }

    override fun hentSakForSøknadsbehandling(søknadsbehandlingId: SøknadsbehandlingId): Sak {
        return sakRepo.hentSakforSøknadsbehandling(søknadsbehandlingId)
    }

    override fun hentSakForVedtak(vedtakId: UUID): Sak? = sakRepo.hentSakForVedtak(vedtakId)

    override fun hentSakForSøknad(søknadId: UUID): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSakForSøknad(søknadId)?.right() ?: FantIkkeSak.left()
    }

    override fun genererFritekstbrevPåSak(request: OpprettDokumentRequest): Either<KunneIkkeOppretteDokument, Dokument.UtenMetadata> {
        val sak = sakRepo.hentSak(request.sakId)
            ?: throw IllegalStateException("Fant ikke sak ved opprettFritekstDokument. sakid ${request.sakId}")

        return brevService.lagDokument(
            FritekstDokumentCommand(
                fødselsnummer = sak.fnr,
                saksnummer = sak.saksnummer,
                sakstype = sak.type,
                brevTittel = request.tittel,
                fritekst = request.fritekst,
                saksbehandler = request.saksbehandler,
                distribusjonstype = request.distribusjonstype,
            ),
        ).mapLeft {
            KunneIkkeOppretteDokument.KunneIkkeLageDokument(it)
        }
    }

    override fun genererLagreOgSendFritekstbrevPåSak(request: OpprettDokumentRequest): Either<KunneIkkeOppretteDokument, Dokument.MedMetadata> {
        return genererFritekstbrevPåSak(request).map {
            it.leggTilMetadata(Dokument.Metadata(sakId = request.sakId), request.distribueringsadresse)
        }.onRight {
            dokumentRepo.lagre(it)
        }
    }

    override fun lagreOgSendOpplastetPdfPåSak(request: JournalførOgSendOpplastetPdfSomBrevCommand): Dokument.MedMetadata {
        /**
         * vi tar for god fisk at sakId finnes. Det vil smelle i databasen hvis sakId(foreign key) ikke finnes
         */
        return request.opprettDokumentMedMetadata(clock).also {
            dokumentRepo.lagre(it)
        }
    }

    override fun hentAlleJournalposter(sakId: UUID): Either<KunneIkkeHenteJournalposter, List<Journalpost>> {
        return sakRepo.hentSakInfo(sakId)?.let {
            journalpostClient.hentJournalposterFor(it.saksnummer)
        } ?: throw IllegalArgumentException("Fant ikke sak ved henting av journalposter. id $sakId")
    }

    override fun opprettSak(sak: NySak) {
        sakRepo.opprettSak(sak).also {
            hentSak(sak.id).fold(
                ifLeft = { log.error("Opprettet sak men feilet ved henting av den.") },
                ifRight = {
                    log.info("Sak med id ${it.id} opprettet")
                },
            )
        }
    }

    override fun hentÅpneBehandlingerForAlleSaker(): List<Behandlingssammendrag> {
        return sakRepo.hentÅpneBehandlinger()
    }

    override fun hentFerdigeBehandlingerForAlleSaker(): List<Behandlingssammendrag> {
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
                    uføre = sakTilBegrensetSakInfo(saker.singleOrNullOrThrow { it.type == Sakstype.UFØRE }),
                    alder = sakTilBegrensetSakInfo(saker.singleOrNullOrThrow { it.type == Sakstype.ALDER }),
                )
            },
        )
    }

    override fun oppdaterFødselsnummer(
        command: OppdaterFødselsnummerPåSakCommand,
    ): Either<KunneIkkeOppdatereFødselsnummer, Sak> {
        val sakId = command.sakId
        val sak = hentSak(sakId).getOrElse {
            throw IllegalStateException("Kunne ikke oppdatere fødselsnummer på sak, fant ikke sak med id $sakId")
        }
        val person = personService.hentPerson(sak.fnr).getOrElse {
            throw IllegalStateException("Kunne ikke oppdatere fødselsnummer på sak, fant ikke person med fnr ${sak.fnr} på sak $sakId")
        }
        if (sak.fnr == person.ident.fnr) {
            return KunneIkkeOppdatereFødselsnummer.SakHarAlleredeSisteFødselsnummer.left()
        }
        sakRepo.oppdaterFødselsnummer(
            sakId = sakId,
            gammeltFnr = sak.fnr,
            nyttFnr = person.ident.fnr,
            endretAv = command.saksbehandler,
            endretTidspunkt = Tidspunkt.now(clock),

        )
        // TODO jah: La Sak.kt ha en funksjon for å oppdatere og flytt logikken dit.
        return hentSak(sakId).getOrElse {
            throw IllegalStateException("Kunne ikke oppdatere fødselsnummer på sak, fant ikke sak med id $sakId")
        }.right()
    }

    override fun hentSakIdSaksnummerOgFnrForAlleSaker(): List<SakInfo> {
        return sakRepo.hentSakIdSaksnummerOgFnrForAlleSaker()
    }

    override fun hentEpsSaksIderForBrukersSak(sakId: UUID): List<UUID> {
        val sak = sakRepo.hentSak(sakId) ?: throw IllegalArgumentException("Fant ikke sak med id $sakId")
        val fnrs = sak.vedtakstidslinje()?.flatMap { it.grunnlagsdata.eps } ?: emptyList()
        return fnrs.map { sakRepo.hentSakInfo(it).map { it.sakId } }.flatMap { it }
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
