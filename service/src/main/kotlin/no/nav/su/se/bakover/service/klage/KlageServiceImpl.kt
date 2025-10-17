package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.journalføring.ErTilknyttetSak
import dokument.domain.journalføring.KunneIkkeSjekkeTilknytningTilSak
import dokument.domain.journalføring.QueryJournalpostClient
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.FerdigstiltOmgjortKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.KanBekrefteKlagevurdering
import no.nav.su.se.bakover.domain.klage.KanGenerereBrevutkast
import no.nav.su.se.bakover.domain.klage.KanLeggeTilFritekstTilAvvistBrev
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageSomKanVurderes
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeFerdigstilleOmgjøringsKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevKommandoForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeKlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenneKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage.FantIkkeKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.klage.brev.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.domain.klage.brev.genererBrevutkastForKlage
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.hentVedtakForId
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class KlageServiceImpl(
    private val sakService: SakService,
    private val klageRepo: KlageRepo,
    private val vedtakService: VedtakService,
    private val brevService: BrevService,
    private val klageClient: KlageClient,
    private val sessionFactory: SessionFactory,
    private val oppgaveService: OppgaveService,
    private val queryJournalpostClient: QueryJournalpostClient,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    val clock: Clock,
) : KlageService {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<StatistikkEventObserver> = observers.toList()

    override fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage> {
        request.validate().getOrElse { return it.left() }
        val sak = sakService.hentSak(request.sakId).getOrElse { return KunneIkkeOppretteKlage.FantIkkeSak.left() }

        if (!sak.kanOppretteKlage()) {
            return KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage.left()
        }
        runBlocking {
            queryJournalpostClient.erTilknyttetSak(request.journalpostId, sak.saksnummer)
        }.fold(
            {
                return KunneIkkeOppretteKlage.FeilVedHentingAvJournalpost(it).left()
            },
            {
                when (it) {
                    ErTilknyttetSak.Ja -> {
                        /*sjekk ok, trenger ikke gjøre noe mer*/
                    }

                    ErTilknyttetSak.Nei -> {
                        return KunneIkkeOppretteKlage.FeilVedHentingAvJournalpost(KunneIkkeSjekkeTilknytningTilSak.JournalpostIkkeKnyttetTilSak)
                            .left()
                    }
                }
            },
        )
        val oppgaveResponse = oppgaveService.opprettOppgave(
            OppgaveConfig.Klage.Saksbehandler(
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                journalpostId = request.journalpostId,
                tilordnetRessurs = request.saksbehandler,
                clock = clock,
                sakstype = sak.type,
            ),
        ).getOrElse {
            return KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()
        }
        // Dette er greit så lenge toKlage ikke kan feile. På det tidspunktet må vi gjøre om rekkefølgen.
        return request.toKlage(
            saksnummer = sak.saksnummer,
            sakstype = sak.type,
            fnr = sak.fnr,
            oppgaveId = oppgaveResponse.oppgaveId,
            clock = clock,
        ).also {
            sessionFactory.withTransactionContext { tx ->
                klageRepo.lagre(it, tx)
                observers.notify(StatistikkEvent.Behandling.Klage.Opprettet(it), tx)
            }
        }.right()
    }

    override fun vilkårsvurder(
        command: VurderKlagevilkårCommand,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw RuntimeException("Fant ikke sak med id ${command.sakId}")
        }
        command.vedtakId?.let {
            val vedtak =
                sak.hentVedtakForId(command.vedtakId) ?: return KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak.left()
            if (!vedtak.skalSendeBrev) {
                // Dersom vi ikke har sendt ut et vedtaksbrev, kan det ikke beklages.
                return KunneIkkeVilkårsvurdereKlage.VedtakSkalIkkeSendeBrev.left()
            }
        }
        val klage = sak.hentKlage(klageId = command.klageId) ?: return KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left()
        return klage.vilkårsvurder(
            saksbehandler = command.saksbehandler,
            vilkårsvurderinger = command.formkrav,
        ).onRight {
            klageRepo.lagre(it)
        }
    }

    override fun bekreftVilkårsvurderinger(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VilkårsvurdertKlage.Bekreftet> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()

        return klage.bekreftVilkårsvurderinger(
            saksbehandler = saksbehandler,
        ).onRight {
            klageRepo.lagre(it)
        }
    }

    override fun vurder(request: KlageVurderingerRequest): Either<KunneIkkeVurdereKlage, VurdertKlage> {
        return request.toDomain().flatMap { requestAsDomain ->
            (
                klageRepo.hentKlage(requestAsDomain.klageId)
                    ?.right()
                    ?: FantIkkeKlage.left()
                )
                .flatMap {
                    (it as? KlageSomKanVurderes)
                        ?.right()
                        ?: KunneIkkeVurdereKlage.UgyldigTilstand(it::class).left()
                }.map {
                    it.vurder(
                        saksbehandler = requestAsDomain.saksbehandler,
                        vurderinger = requestAsDomain.vurderinger,
                    ).also { vurdertKlage ->
                        klageRepo.lagre(vurdertKlage)
                    }
                }
        }
    }

    override fun bekreftVurderinger(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VurdertKlage.Bekreftet> {
        return (
            klageRepo.hentKlage(klageId)
                ?.right()
                ?: KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()
            )
            .flatMap {
                (it as? KanBekrefteKlagevurdering)
                    ?.right()
                    ?: KunneIkkeBekrefteKlagesteg.UgyldigTilstand(it::class, VurdertKlage.Bekreftet::class).left()
            }.map {
                it.bekreftVurderinger(
                    saksbehandler = saksbehandler,
                ).also { bekreftetVurdertKlage ->
                    klageRepo.lagre(bekreftetVurdertKlage)
                }
            }
    }

    override fun leggTilAvvistFritekstTilBrev(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeLeggeTilFritekstForAvvist, AvvistKlage> {
        return (
            klageRepo.hentKlage(klageId)
                ?.right()
                ?: KunneIkkeLeggeTilFritekstForAvvist.FantIkkeKlage.left()
            )
            .flatMap {
                (it as? KanLeggeTilFritekstTilAvvistBrev)
                    ?.right()
                    ?: KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand(it::class).left()
            }.map {
                it.leggTilFritekstTilAvvistVedtaksbrev(
                    saksbehandler = saksbehandler,
                    fritekstTilAvvistVedtaksbrev = fritekst,
                ).also { avvistKlage ->
                    klageRepo.lagre(avvistKlage)
                }
            }
    }

    override fun sendTilAttestering(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeKlageTilAttestering, KlageTilAttestering> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeSendeKlageTilAttestering.FantIkkeKlage.left()
        return klage.sendTilAttestering(saksbehandler).onRight {
            klageRepo.lagre(it)
            oppgaveService.oppdaterOppgave(
                oppgaveId = klage.oppgaveId,
                oppdaterOppgaveInfo = OppdaterOppgaveInfo(
                    beskrivelse = "Sendt klagen til attestering",
                    oppgavetype = Oppgavetype.ATTESTERING,
                    tilordnetRessurs = klage.attesteringer.prøvHentSisteAttestering()?.attestant?.navIdent?.let {
                        OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(it)
                    } ?: OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs,
                ),
            ).mapLeft {
                log.error("Send klagebehandling til attestering: Feil ved oppdatering av oppgave ${klage.oppgaveId}, for klage ${klage.id}. Feilen var $it")
            }
        }
    }

    override fun ferdigstillOmgjøring(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeFerdigstilleOmgjøringsKlage, FerdigstiltOmgjortKlage> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeFerdigstilleOmgjøringsKlage.FantIkkeKlage.left()
        val ferdigstiltTidspunkt = Tidspunkt.now(clock)

        return klage.ferdigstillOmgjøring(saksbehandler, ferdigstiltTidspunkt).onRight {
            sessionFactory.withTransactionContext { tx ->
                klageRepo.lagre(it, tx)
                observers.notify(StatistikkEvent.Behandling.Klage.FerdigstiltOmgjøring(it), tx)
                oppgaveService.lukkOppgave(
                    it.oppgaveId,
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(saksbehandler.navIdent),
                ).mapLeft {
                    log.error("Ferdigstill omgjøring klagebehandling. Feil ved oppdatering av oppgave ${klage.oppgaveId}, for klage ${klage.id}. Feilen var $it")
                }
            }
        }
    }

    override fun underkjenn(request: UnderkjennKlageRequest): Either<KunneIkkeUnderkjenneKlage, Klage> {
        val klage = klageRepo.hentKlage(request.klageId) ?: return KunneIkkeUnderkjenneKlage.FantIkkeKlage.left()
        return klage.underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = request.attestant,
                opprettet = Tidspunkt.now(clock),
                grunn = request.grunn,
                kommentar = request.kommentar,
            ),
        ).onRight {
            klageRepo.lagre(it)
            oppgaveService.oppdaterOppgave(
                oppgaveId = klage.oppgaveId,
                oppdaterOppgaveInfo = OppdaterOppgaveInfo(
                    beskrivelse = "Klagen er blitt underkjent",
                    oppgavetype = Oppgavetype.BEHANDLE_SAK,
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(klage.saksbehandler.navIdent),
                ),
            ).mapLeft {
                log.error("Underkjenn klagebehandling: Feil ved oppdatering av oppgave ${klage.oppgaveId}, for klage ${klage.id}. Feilen var $it")
            }
        }
    }

    override fun oversend(
        sakId: UUID,
        klageId: KlageId,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeOversendeKlage, OversendtKlage> {
        val sak = sakService.hentSak(sakId)
            .getOrElse {
                throw java.lang.IllegalStateException("Kunne ikke generere brevutkast for sak. Fant ikke sak med id $sakId og klageId $klageId")
            }

        val klage = (
            sak.hentKlage(klageId)
                ?: run {
                    log.error("Kunne ikke generere brevutkast for sak. Fant ikke klage med id $klageId på sak $sakId")
                    return KunneIkkeOversendeKlage.FantIkkeKlage.left()
                }

            ).let {
            (it as? KlageTilAttestering.Vurdert) ?: return KunneIkkeOversendeKlage.UgyldigTilstand(it::class).left()
        }
        val vedtakId = klage.vilkårsvurderinger.vedtakId
        val oversendtKlage =
            klage.oversend(Attestering.Iverksatt(attestant = attestant, opprettet = Tidspunkt.now(clock)))
                .getOrElse { return it.left() }

        val dokument = oversendtKlage.genererOversendelsesbrev(
            hentVedtaksbrevDato = { hentVedtaksbrevDatoForKlage(sakId, vedtakId, klageId) },
        ).getOrElse {
            return KunneIkkeOversendeKlage.KunneIkkeLageBrevRequest(it).left()
        }.let {
            brevService.lagDokument(command = it).getOrElse {
                return KunneIkkeOversendeKlage.KunneIkkeLageDokument(it).left()
            }
        }.leggTilMetadata(
            Dokument.Metadata(
                klageId = klage.id.value,
                sakId = klage.sakId,
            ),
            // kan ikke sende brev til en annen adresse enn brukerens adresse per nå
            distribueringsadresse = null,
        )

        val journalpostIdForVedtak = hentJournalpostIdForVedtakId(sakId, vedtakId)
            ?: return KunneIkkeOversendeKlage.FantIkkeJournalpostIdKnyttetTilVedtaket.left().onLeft {
                log.error("Kunne ikke iverksette klage ${oversendtKlage.id} fordi vi ikke fant journalpostId til vedtak $vedtakId (kan tyde på at klagen er knyttet til et vedtak vi ikke har laget brev for eller at databasen er i en ugyldig tilstand.)")
            }

        class KunneIkkeOversendeTilKlageinstansEx : RuntimeException()
        try {
            sessionFactory.withTransactionContext { tx ->
                brevService.lagreDokument(dokument, tx)
                klageRepo.lagre(oversendtKlage, tx)
                observers.notify(StatistikkEvent.Behandling.Klage.Oversendt(oversendtKlage), tx)
                klageClient.sendTilKlageinstans(
                    klage = oversendtKlage,
                    journalpostIdForVedtak = journalpostIdForVedtak,
                ).getOrElse { throw KunneIkkeOversendeTilKlageinstansEx() }
            }
        } catch (_: KunneIkkeOversendeTilKlageinstansEx) {
            return KunneIkkeOversendeKlage.KunneIkkeOversendeTilKlageinstans.left()
        }
        oppgaveService.lukkOppgave(
            oversendtKlage.oppgaveId,
            tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(attestant.navIdent),
        )

        return oversendtKlage.right()
    }

    override fun iverksettAvvistKlage(
        klageId: KlageId,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteAvvistKlage, IverksattAvvistKlage> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeIverksetteAvvistKlage.FantIkkeKlage.left()

        if (klage !is KlageTilAttestering.Avvist) {
            return KunneIkkeIverksetteAvvistKlage.UgyldigTilstand(
                klage::class,
            ).left()
        }

        val avvistKlage = klage.iverksett(
            Attestering.Iverksatt(
                attestant = attestant,
                opprettet = Tidspunkt.now(clock),
            ),
        ).getOrElse {
            return it.left()
        }

        val vedtak = Klagevedtak.Avvist.fromIverksattAvvistKlage(avvistKlage, clock)
        val dokument = avvistKlage.lagAvvistVedtaksbrevKommando().getOrElse {
            return KunneIkkeIverksetteAvvistKlage.KunneIkkeLageBrevRequest(it).left()
        }.let { command ->
            brevService.lagDokument(command = command).getOrElse {
                return KunneIkkeIverksetteAvvistKlage.KunneIkkeLageBrev(it).left()
            }
        }.leggTilMetadata(
            Dokument.Metadata(
                klageId = klage.id.value,
                sakId = klage.sakId,
                vedtakId = vedtak.id,
            ),
            // kan ikke sende brev til en annen adresse enn brukerens adresse per nå
            distribueringsadresse = null,
        )
        try {
            sessionFactory.withTransactionContext { tx ->
                klageRepo.lagre(avvistKlage, tx)
                vedtakService.lagreITransaksjon(vedtak, tx)
                brevService.lagreDokument(dokument, tx)
                observers.notify(StatistikkEvent.Behandling.Klage.Avvist(vedtak), tx)
            }
        } catch (_: Exception) {
            return KunneIkkeIverksetteAvvistKlage.FeilVedLagringAvDokumentOgKlage.left()
        }

        oppgaveService.lukkOppgave(
            avvistKlage.oppgaveId,
            tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(attestant.navIdent),
        )
        return avvistKlage.right()
    }

    override fun brevutkast(
        sakId: UUID,
        klageId: KlageId,
        ident: NavIdentBruker,
    ): Either<KunneIkkeLageBrevutkast, PdfA> {
        val sak = sakService.hentSak(sakId)
            .getOrElse { throw IllegalStateException("Kunne ikke generere brevutkast for sak. Fant ikke sak med id $sakId og klageId $klageId") }
        val klage = sak.hentKlage(klageId)
            ?: run {
                log.error("Kunne ikke generere brevutkast for sak. Fant ikke klage med id $klageId på sak $sakId")
                return KunneIkkeLageBrevutkast.FantIkkeKlage.left()
            }
        (klage as? KanGenerereBrevutkast) ?: return KunneIkkeLageBrevutkast.FeilVedBrevRequest(
            KunneIkkeLageBrevKommandoForKlage.UgyldigTilstand(fra = klage::class),
        ).left()
        val vedtakId = klage.vilkårsvurderinger?.vedtakId ?: return KunneIkkeLageBrevutkast.FeilVedBrevRequest(
            KunneIkkeLageBrevKommandoForKlage.UgyldigTilstand(klage::class),
        ).left()
        val vedtaksbrevdato = hentVedtaksbrevDatoForKlage(sakId, vedtakId, klageId)
            ?: run {
                log.error("Kunne ikke generere brevutkast for sak. Fant ikke vedtaksbrevdato for sak $sakId og vedtakId $vedtakId")
                return KunneIkkeLageBrevutkast.FeilVedBrevRequest(KunneIkkeLageBrevKommandoForKlage.FeilVedHentingAvVedtaksbrevDato)
                    .left()
            }
        return genererBrevutkastForKlage(
            klageId = klageId,
            ident = ident,
            hentKlage = { klage },
            hentVedtaksbrevDato = { vedtaksbrevdato },
            genererPdf = {
                brevService.lagDokument(command = it).map { it.generertDokument }
            },
        )
    }

    private fun hentVedtaksbrevDatoForKlage(
        sakId: UUID,
        vedtakId: UUID,
        klageId: KlageId,
    ): LocalDate? {
        return (
            klageRepo.hentVedtaksbrevDatoSomDetKlagesPå(klageId)
                ?: dokumentHendelseRepo.hentVedtaksbrevdatoForSakOgVedtakId(sakId, vedtakId)
            )
    }

    private fun hentJournalpostIdForVedtakId(sakId: UUID, vedtakId: UUID): JournalpostId? {
        return vedtakService.hentJournalpostId(vedtakId)
            ?: dokumentHendelseRepo.hentJournalpostIdForSakOgVedtakId(sakId, vedtakId)
    }

    override fun avslutt(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
    ): Either<KunneIkkeAvslutteKlage, AvsluttetKlage> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeAvslutteKlage.FantIkkeKlage.left()

        return klage.avslutt(
            saksbehandler = saksbehandler,
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = Tidspunkt.now(clock),
        ).onRight {
            sessionFactory.withTransactionContext { tx ->
                klageRepo.lagre(it, tx)
                observers.notify(StatistikkEvent.Behandling.Klage.Avsluttet(it), tx)
                oppgaveService.lukkOppgave(
                    it.oppgaveId,
                    OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(saksbehandler.navIdent),
                )
            }
        }
    }
}
