package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.journalpost.ErTilknyttetSak
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkeTilknytningTilSak
import no.nav.su.se.bakover.domain.journalpost.QueryJournalpostClient
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.KanBekrefteKlagevurdering
import no.nav.su.se.bakover.domain.klage.KanLeggeTilFritekstTilAvvistBrev
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KlageId
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageSomKanVurderes
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
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
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock

class KlageServiceImpl(
    private val sakService: SakService,
    private val klageRepo: KlageRepo,
    private val vedtakService: VedtakService,
    private val brevService: BrevService,
    private val personService: PersonService,
    private val klageClient: KlageClient,
    private val sessionFactory: SessionFactory,
    private val oppgaveService: OppgaveService,
    private val queryJournalpostClient: QueryJournalpostClient,
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

        val aktørId = personService.hentAktørId(sak.fnr).getOrElse {
            return KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()
        }
        val oppgaveResponse = oppgaveService.opprettOppgave(
            OppgaveConfig.Klage.Saksbehandler(
                saksnummer = sak.saksnummer,
                aktørId = aktørId,
                journalpostId = request.journalpostId,
                tilordnetRessurs = request.saksbehandler,
                clock = clock,
            ),
        ).getOrElse {
            return KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()
        }
        // Dette er greit så lenge toKlage ikke kan feile. På det tidspunktet må vi gjøre om rekkefølgen.
        return request.toKlage(
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            oppgaveId = oppgaveResponse.oppgaveId,
            clock = clock,
        ).also {
            klageRepo.lagre(it)
            observers.notify(StatistikkEvent.Behandling.Klage.Opprettet(it))
        }.right()
    }

    override fun vilkårsvurder(request: VurderKlagevilkårRequest): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return request.toDomain().flatMap {
            it.vilkårsvurderinger.vedtakId?.let { vedtakId ->
                if (vedtakService.hentForVedtakId(vedtakId) == null) {
                    return KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak.left()
                }
            }
            val klage = klageRepo.hentKlage(it.klageId) ?: return KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left()
            klage.vilkårsvurder(
                saksbehandler = it.saksbehandler,
                vilkårsvurderinger = it.vilkårsvurderinger,
            )
        }.onRight {
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
        return request.toDomain().flatMap { r ->
            (
                klageRepo.hentKlage(r.klageId)
                    ?.right()
                    ?: FantIkkeKlage.left()
                )
                .flatMap {
                    (it as? KlageSomKanVurderes)
                        ?.right()
                        ?: KunneIkkeVurdereKlage.UgyldigTilstand(it::class).left()
                }.map {
                    it.vurder(
                        saksbehandler = r.saksbehandler,
                        vurderinger = r.vurderinger,
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
                    tilordnetRessurs = klage.attesteringer.prøvHentSisteAttestering()?.attestant?.navIdent,
                ),
            ).mapLeft {
                log.error("Feil ved oppdatering av oppgave ${klage.oppgaveId}, for klage ${klage.id}. Feilen var $it")
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
                    tilordnetRessurs = klage.saksbehandler.navIdent,
                ),
            ).mapLeft {
                log.error("Feil ved oppdatering av oppgave ${klage.oppgaveId}, for klage ${klage.id}. Feilen var $it")
            }
        }
    }

    override fun oversend(
        klageId: KlageId,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeOversendeKlage, OversendtKlage> {
        val klage = (klageRepo.hentKlage(klageId) ?: return KunneIkkeOversendeKlage.FantIkkeKlage.left()).let {
            it as? KlageTilAttestering.Vurdert ?: return KunneIkkeOversendeKlage.UgyldigTilstand(it::class)
                .left()
        }

        val oversendtKlage =
            klage.oversend(Attestering.Iverksatt(attestant = attestant, opprettet = Tidspunkt.now(clock)))
                .getOrElse { return it.left() }

        val dokument = oversendtKlage.genererOversendelsesbrev(
            hentVedtaksbrevDato = { klageRepo.hentVedtaksbrevDatoSomDetKlagesPå(klage.id) },
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
        )

        val journalpostIdForVedtak = vedtakService.hentJournalpostId(oversendtKlage.vilkårsvurderinger.vedtakId)
            ?: return KunneIkkeOversendeKlage.FantIkkeJournalpostIdKnyttetTilVedtaket.left().onLeft {
                log.error("Kunne ikke iverksette klage ${oversendtKlage.id} fordi vi ikke fant journalpostId til vedtak ${oversendtKlage.vilkårsvurderinger.vedtakId} (kan tyde på at klagen er knyttet til et vedtak vi ikke har laget brev for eller at databasen er i en ugyldig tilstand.)")
            }

        class KunneIkkeOversendeTilKlageinstansEx : RuntimeException()
        try {
            sessionFactory.withTransactionContext {
                brevService.lagreDokument(dokument, it)
                klageRepo.lagre(oversendtKlage, it)

                klageClient.sendTilKlageinstans(
                    klage = oversendtKlage,
                    journalpostIdForVedtak = journalpostIdForVedtak,
                ).getOrElse { throw KunneIkkeOversendeTilKlageinstansEx() }
            }
        } catch (_: KunneIkkeOversendeTilKlageinstansEx) {
            return KunneIkkeOversendeKlage.KunneIkkeOversendeTilKlageinstans.left()
        }
        oppgaveService.lukkOppgave(oversendtKlage.oppgaveId)
        observers.notify(StatistikkEvent.Behandling.Klage.Oversendt(oversendtKlage))
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
        )
        try {
            sessionFactory.withTransactionContext {
                klageRepo.lagre(avvistKlage, it)
                vedtakService.lagreITransaksjon(vedtak, it)
                brevService.lagreDokument(dokument, it)
            }
        } catch (_: Exception) {
            return KunneIkkeIverksetteAvvistKlage.FeilVedLagringAvDokumentOgKlage.left()
        }

        oppgaveService.lukkOppgave(avvistKlage.oppgaveId)
        observers.notify(StatistikkEvent.Behandling.Klage.Avvist(vedtak))
        return avvistKlage.right()
    }

    override fun brevutkast(
        klageId: KlageId,
        ident: NavIdentBruker,
    ): Either<KunneIkkeLageBrevutkast, PdfA> {
        return genererBrevutkastForKlage(
            klageId = klageId,
            ident = ident,
            hentKlage = { klageRepo.hentKlage(klageId) },
            hentVedtaksbrevDato = { klageRepo.hentVedtaksbrevDatoSomDetKlagesPå(klageId) },
            genererPdf = {
                brevService.lagDokument(command = it).map { it.generertDokument }
            },
        )
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
            klageRepo.lagre(it)
            oppgaveService.lukkOppgave(it.oppgaveId)
            observers.notify(StatistikkEvent.Behandling.Klage.Avsluttet(it))
        }
    }
}
