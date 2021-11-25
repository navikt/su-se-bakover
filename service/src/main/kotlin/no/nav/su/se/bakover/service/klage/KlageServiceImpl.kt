package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.person.PersonService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
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
    val clock: Clock,
) : KlageService {

    override fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage> {

        val sak = sakRepo.hentSak(request.sakId) ?: return KunneIkkeOppretteKlage.FantIkkeSak.left()

        sak.klager.ifNotEmpty {
            // TODO jah: Justere denne sjekken når vi har konseptet lukket klage.
            return KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage.left()
        }
        return request.toKlage(clock).also {
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

    override fun bekreftVilkårsvurderinger(klageId: UUID): Either<KunneIkkeBekrefteKlagesteg, VilkårsvurdertKlage> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()

        // TODO jah: Flytt domenelogikken inn i domenet
        if (klage !is VilkårsvurdertKlage.Utfylt) {
            return KunneIkkeBekrefteKlagesteg.UgyldigTilstand(klage::class, VilkårsvurdertKlage.Bekreftet::class).left()
        }

        return klage.bekreft().also {
            klageRepo.lagre(it)
        }.right()
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

    override fun bekreftVurderinger(klageId: UUID): Either<KunneIkkeBekrefteKlagesteg, VurdertKlage> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()

        // TODO jah: Flytt domenelogikken inn i domenet
        if (klage !is VurdertKlage.Utfylt) {
            return KunneIkkeBekrefteKlagesteg.UgyldigTilstand(klage::class, VurdertKlage.Bekreftet::class).left()
        }

        return klage.bekreft().also {
            klageRepo.lagre(it)
        }.right()
    }

    override fun sendTilAttestering(klageId: UUID): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
        val klage = klageRepo.hentKlage(klageId) ?: return KunneIkkeSendeTilAttestering.FantIkkeKlage.left()

        if (klage !is VurdertKlage.Bekreftet) {
            return KunneIkkeSendeTilAttestering.UgyldigTilstand(klage::class, KlageTilAttestering::class).left()
        }

        return klage.sendTilAttestering().tap {
            klageRepo.lagre(it)
        }
    }

    override fun brevutkast(
        sakId: UUID,
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
        hjemler: Hjemler.Utfylt,
    ): Either<KunneIkkeLageBrevutkast, ByteArray> {
        val sak = sakRepo.hentSak(sakId) ?: return KunneIkkeLageBrevutkast.FantIkkeSak.left()

        return personService.hentPerson(sak.fnr)
            .fold(
                ifLeft = { KunneIkkeLageBrevutkast.FantIkkePerson.left() },
                ifRight = { person ->
                    val brevRequest = LagBrevRequest.Klage.Oppretthold(
                        person = person,
                        dagensDato = LocalDate.now(clock),
                        saksbehandlerNavn = microsoftGraphApiClient.hentNavnForNavIdent(saksbehandler)
                            .getOrElse { return KunneIkkeLageBrevutkast.FantIkkeSaksbehandler.left() },
                        fritekst = fritekst,
                        hjemler = hjemler.hjemler.map {
                            it.paragrafnummer
                        },
                    )

                    brevService.lagBrev(brevRequest)
                        .mapLeft { KunneIkkeLageBrevutkast.GenereringAvBrevFeilet }
                },
            )
    }
}
