package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.IverksattKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class KlageServiceImpl(
    private val sakRepo: SakRepo,
    private val klageRepo: KlageRepo,
    private val vedtakRepo: VedtakRepo,
    private val brevService: BrevService,
    private val personService: PersonService,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val klageClient: KlageClient,
    private val sessionFactory: SessionFactory,
    private val oppgaveService: OppgaveService,
    val clock: Clock,
) : KlageService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage> {

        val sak = sakRepo.hentSak(request.sakId) ?: return KunneIkkeOppretteKlage.FantIkkeSak.left()

        sak.hentÅpneKlager().ifNotEmpty {
            // TODO jah: Justere denne sjekken når vi har konseptet lukket klage.
            return KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage.left()
        }
        val aktørId = personService.hentAktørId(sak.fnr).getOrElse {
            return KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()
        }
        val oppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.Klage.Saksbehandler(
                saksnummer = sak.saksnummer,
                aktørId = aktørId,
                journalpostId = request.journalpostId,
                tilordnetRessurs = null,
                clock = clock,
            ),
        ).getOrHandle {
            return KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()
        }
        // Dette er greit så lenge toKlage ikke kan feile. På det tidspunktet må vi gjøre om rekkefølgen.
        return request.toKlage(
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            oppgaveId = oppgaveId,
            clock = clock,
        ).also {
            klageRepo.lagre(it)
        }.right()
    }

    override fun vilkårsvurder(request: VurderKlagevilkårRequest): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return request.toDomain().flatMap {
            it.vilkårsvurderinger.vedtakId?.let { vedtakId ->
                if (vedtakRepo.hentForVedtakId(vedtakId) == null) {
                    return KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak.left()
                }
            }
            val klage = klageRepo.hentKlage(it.klageId) ?: return KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left()
            klage.vilkårsvurder(
                saksbehandler = it.saksbehandler,
                vilkårsvurderinger = it.vilkårsvurderinger,
            )
        }.tap {
            klageRepo.lagre(it)
        }
    }

    override fun bekreftVilkårsvurderinger(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VilkårsvurdertKlage.Bekreftet> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()

        return klage.bekreftVilkårsvurderinger(
            saksbehandler = saksbehandler,
        ).tap {
            klageRepo.lagre(it)
        }
    }

    override fun vurder(request: KlageVurderingerRequest): Either<KunneIkkeVurdereKlage, VurdertKlage> {
        return request.toDomain().flatMap {
            val klage = klageRepo.hentKlage(it.klageId) ?: return KunneIkkeVurdereKlage.FantIkkeKlage.left()

            klage.vurder(
                saksbehandler = it.saksbehandler,
                vurderinger = it.vurderinger,
            ).tap { vurdertKlage ->
                klageRepo.lagre(vurdertKlage)
            }
        }
    }

    override fun bekreftVurderinger(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VurdertKlage.Bekreftet> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()

        return klage.bekreftVurderinger(saksbehandler = saksbehandler).tap {
            klageRepo.lagre(it)
        }
    }

    override fun sendTilAttestering(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeSendeTilAttestering.FantIkkeKlage.left()
        val oppgaveIdSomSkalLukkes = klage.oppgaveId
        return klage.sendTilAttestering(saksbehandler) {
            personService.hentAktørId(klage.fnr).flatMap { aktørId ->
                oppgaveService.opprettOppgave(
                    OppgaveConfig.Klage.Saksbehandler(
                        saksnummer = klage.saksnummer,
                        aktørId = aktørId,
                        journalpostId = klage.journalpostId,
                        tilordnetRessurs = (klage as? VurdertKlage)?.attesteringer?.map { it.attestant }?.lastOrNull(),
                        clock = clock,
                    ),
                )
            }.mapLeft {
                KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave
            }
        }.tap {
            klageRepo.lagre(it)
            oppgaveService.lukkOppgave(oppgaveIdSomSkalLukkes)
        }
    }

    override fun underkjenn(request: UnderkjennKlageRequest): Either<KunneIkkeUnderkjenne, VurdertKlage.Bekreftet> {
        val klage = klageRepo.hentKlage(request.klageId) ?: return KunneIkkeUnderkjenne.FantIkkeKlage.left()
        val oppgaveIdSomSkalLukkes = klage.oppgaveId
        return klage.underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = request.attestant,
                opprettet = Tidspunkt.now(clock),
                grunn = request.grunn,
                kommentar = request.kommentar,
            ),
        ) {
            personService.hentAktørId(klage.fnr).flatMap {
                oppgaveService.opprettOppgave(
                    OppgaveConfig.Klage.Saksbehandler(
                        saksnummer = klage.saksnummer,
                        aktørId = it,
                        journalpostId = klage.journalpostId,
                        tilordnetRessurs = klage.saksbehandler,
                        clock = clock,
                    ),
                )
            }.mapLeft {
                KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave
            }
        }.tap {
            klageRepo.lagre(it)
            oppgaveService.lukkOppgave(oppgaveIdSomSkalLukkes)
        }
    }

    override fun iverksett(
        klageId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteKlage, IverksattKlage> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeIverksetteKlage.FantIkkeKlage.left()

        val iverksattKlage = klage.iverksett(
            Attestering.Iverksatt(
                attestant = attestant,
                opprettet = Tidspunkt.now(clock),
            ),
        ).getOrHandle { return it.left() }

        val hjemler: Hjemler.Utfylt =
            (iverksattKlage.vurderinger.vedtaksvurdering as? VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold)?.hjemler
                ?: throw IllegalStateException("Vedtaksvurderingene skal ikke kunne være noe annet enn Utfylt.Oppretthold på dette tidspunkte i MVPen til klage ${iverksattKlage.id}")

        val dokument = lagBrevRequest(
            klage = iverksattKlage,
            saksbehandler = iverksattKlage.saksbehandler,
            fritekstTilBrev = iverksattKlage.vurderinger.fritekstTilBrev,
            hjemler = hjemler,
        ).flatMap {
            it.tilDokument { brevRequest ->
                brevService.lagBrev(brevRequest).mapLeft {
                    LagBrevRequest.KunneIkkeGenererePdf
                }
            }.mapLeft {
                KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF
            }
        }.map {
            it.leggTilMetadata(
                Dokument.Metadata(
                    klageId = klage.id,
                    sakId = klage.sakId,
                    bestillBrev = true,
                ),
            )
        }.getOrHandle {
            return KunneIkkeIverksetteKlage.KunneIkkeLageBrev(it).left()
        }

        val journalpostIdForVedtak = vedtakRepo.hentJournalpostId(iverksattKlage.vilkårsvurderinger.vedtakId)
            ?: return KunneIkkeIverksetteKlage.FantIkkeJournalpostIdKnyttetTilVedtaket.left().tapLeft {
                log.error("Kunne ikke iverksette klage ${iverksattKlage.id} fordi vi ikke fant journalpostId til vedtak ${iverksattKlage.vilkårsvurderinger.vedtakId} (kan tyde på at klagen er knyttet til et vedtak vi ikke har laget brev for eller at databasen er i en ugyldig tilstand.)")
            }

        class KunneIkkeOversendeTilKlageinstansEx : RuntimeException()
        try {
            sessionFactory.withTransactionContext {
                brevService.lagreDokument(dokument, it)
                klageRepo.lagre(iverksattKlage, it)

                klageClient.sendTilKlageinstans(
                    klage = iverksattKlage,
                    saksnummer = klage.saksnummer,
                    fnr = klage.fnr,
                    journalpostIdForVedtak = journalpostIdForVedtak,
                ).getOrHandle { throw KunneIkkeOversendeTilKlageinstansEx() }
            }
        } catch (_: KunneIkkeOversendeTilKlageinstansEx) {
            return KunneIkkeIverksetteKlage.KunneIkkeOversendeTilKlageinstans.left()
        }
        oppgaveService.lukkOppgave(iverksattKlage.oppgaveId)
        return iverksattKlage.right()
    }

    override fun brevutkast(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
        hjemler: Hjemler.Utfylt,
    ): Either<KunneIkkeLageBrevutkast, ByteArray> {

        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeLageBrevutkast.FantIkkeKlage.left()

        return lagBrevRequest(
            klage = klage,
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekst,
            hjemler = hjemler,
        ).mapLeft {
            KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(it)
        }.flatMap {
            brevService.lagBrev(it).mapLeft { kunneIkkeLageBrev ->
                when (kunneIkkeLageBrev) {
                    no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev.FantIkkePerson -> KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(
                        KunneIkkeLageBrevForKlage.FantIkkePerson,
                    )
                    no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev.KunneIkkeGenererePDF -> KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(
                        KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF,
                    )
                }
            }
        }
    }

    private fun lagBrevRequest(
        klage: Klage,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekstTilBrev: String,
        hjemler: Hjemler.Utfylt,
    ): Either<KunneIkkeLageBrevForKlage, LagBrevRequest.Klage.Oppretthold> {
        val saksbehandlerNavn = microsoftGraphApiClient.hentNavnForNavIdent(saksbehandler)
            .getOrElse { return KunneIkkeLageBrevForKlage.FantIkkeSaksbehandler.left() }
        val vedtakDato =
            klageRepo.hentKnyttetVedtaksdato(klage.id)
                ?: return KunneIkkeLageBrevForKlage.FantIkkeVedtakKnyttetTilKlagen.left()

        return personService.hentPerson(klage.fnr).map { person ->
            lagBrevRequestForOppretthold(
                person = person,
                saksbehandlerNavn = saksbehandlerNavn,
                hjemler = hjemler,
                fritekst = fritekstTilBrev,
                klageDato = klage.datoKlageMottatt,
                vedtakDato = vedtakDato,
            )
        }.mapLeft {
            KunneIkkeLageBrevForKlage.FantIkkePerson
        }
    }

    private fun lagBrevRequestForOppretthold(
        person: Person,
        saksbehandlerNavn: String,
        hjemler: Hjemler.Utfylt,
        fritekst: String,
        klageDato: LocalDate,
        vedtakDato: LocalDate,
    ): LagBrevRequest.Klage.Oppretthold {
        return LagBrevRequest.Klage.Oppretthold(
            person = person,
            dagensDato = LocalDate.now(clock),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
            hjemler = hjemler.map {
                it.paragrafnummer
            },
            klageDato = klageDato,
            vedtakDato = vedtakDato,
        )
    }
}
