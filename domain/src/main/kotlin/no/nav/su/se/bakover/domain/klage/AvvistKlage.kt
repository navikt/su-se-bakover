package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import kotlin.reflect.KClass

/**
 * Representerer en klage når minst et av formkravene er besvart 'nei/false'
 * forrige-klasse: [VilkårsvurdertKlage.Bekreftet.Avvist]
 * neste-klasse: [KlageTilAttestering.Avvist]
 */
sealed interface AvvistKlage : VilkårsvurdertKlage {
    val fritekstTilBrev: String?

    /**
     * Representerer en avvist klage der fritekst er blitt påbegynt, og har blitt lagret
     * forrige-steg: [VilkårsvurdertKlage.Bekreftet.Avvist]
     * neste-steg: [AvvistKlage.Bekreftet]
     */
    data class Påbegynt private constructor(
        private val forrigeSteg: VilkårsvurdertKlage.Bekreftet.Avvist,
        override val fritekstTilBrev: String?,
    ) : AvvistKlage, VilkårsvurdertKlage by forrigeSteg {
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt = forrigeSteg.vilkårsvurderinger

        override fun vurder(
            saksbehandler: NavIdentBruker.Saksbehandler,
            vurderinger: VurderingerTilKlage,
        ): Either<KunneIkkeVurdereKlage.UgyldigTilstand, VurdertKlage> {
            return super<AvvistKlage>.vurder(saksbehandler, vurderinger)
        }

        override fun bekreftVurderinger(saksbehandler: NavIdentBruker.Saksbehandler): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VurdertKlage.Bekreftet> {
            return super<AvvistKlage>.bekreftVurderinger(saksbehandler)
        }

        override fun sendTilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
            opprettOppgave: () -> Either<KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
        ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
            return super<AvvistKlage>.sendTilAttestering(saksbehandler, opprettOppgave)
        }

        override fun leggTilAvvistFritekstTilBrev(
            saksbehandler: NavIdentBruker.Saksbehandler,
            fritekst: String?,
        ): Either<KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand, Påbegynt> {
            return create(
                forrigeSteg = forrigeSteg,
                fritekstTilBrev = fritekst,
            ).right()
        }

        override fun bekreftAvvistFritekstTilBrev(saksbehandler: NavIdentBruker.Saksbehandler): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, Bekreftet> {
            return Bekreftet.create(
                forrigeSteg = forrigeSteg.copy(saksbehandler = saksbehandler),
                fritekstTilBrev = fritekstTilBrev
                    ?: throw IllegalStateException("Må ha fyllt inn fritekst for å bekrefte. id: $id"),
            ).right()
        }

        companion object {
            fun create(
                forrigeSteg: VilkårsvurdertKlage.Bekreftet.Avvist,
                fritekstTilBrev: String?,
            ): Påbegynt {
                return Påbegynt(forrigeSteg, fritekstTilBrev)
            }
        }
    }

    /**
     * Representerer en avvist klage der fritekst er bekreftet
     * forrige-steg: [AvvistKlage.Påbegynt]
     * neste-steg: [KlageTilAttestering.Avvist]
     */
    data class Bekreftet private constructor(
        private val forrigeSteg: VilkårsvurdertKlage.Bekreftet.Avvist,
        override val fritekstTilBrev: String,
    ) : AvvistKlage, VilkårsvurdertKlage by forrigeSteg {
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt = forrigeSteg.vilkårsvurderinger

        override fun vurder(
            saksbehandler: NavIdentBruker.Saksbehandler,
            vurderinger: VurderingerTilKlage,
        ): Either<KunneIkkeVurdereKlage.UgyldigTilstand, VurdertKlage> {
            return super<AvvistKlage>.vurder(saksbehandler, vurderinger)
        }

        override fun bekreftVurderinger(saksbehandler: NavIdentBruker.Saksbehandler): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VurdertKlage.Bekreftet> {
            return super<AvvistKlage>.bekreftVurderinger(saksbehandler)
        }

        override fun leggTilAvvistFritekstTilBrev(
            saksbehandler: NavIdentBruker.Saksbehandler,
            fritekst: String?,
        ): Either<KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand, Påbegynt> {
            return Påbegynt.create(
                forrigeSteg = forrigeSteg,
                fritekstTilBrev = fritekst,
            ).right()
        }

        override fun bekreftAvvistFritekstTilBrev(saksbehandler: NavIdentBruker.Saksbehandler): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, Bekreftet> {
            return Bekreftet(
                forrigeSteg = forrigeSteg.copy(saksbehandler = saksbehandler),
                fritekstTilBrev = fritekstTilBrev,
            ).right()
        }

        override fun sendTilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
            opprettOppgave: () -> Either<KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
        ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
            return opprettOppgave().map { oppgaveId ->
                KlageTilAttestering.Avvist.create(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    saksbehandler = saksbehandler,
                    vilkårsvurderinger = vilkårsvurderinger,
                    vurderinger = vurderinger,
                    attesteringer = attesteringer,
                    datoKlageMottatt = datoKlageMottatt,
                    fritekstTilBrev = fritekstTilBrev,
                )
            }
        }

        override fun oversend(iverksattAttestering: Attestering.Iverksatt): Either<KunneIkkeOversendeKlage, OversendtKlage> {
            return super<AvvistKlage>.oversend(iverksattAttestering)
        }

        override fun avvis(iverksattAttestering: Attestering.Iverksatt): Either<KunneIkkeIverksetteAvvistKlage, IverksattAvvistKlage> {
            return super<AvvistKlage>.avvis(iverksattAttestering)
        }

        companion object {
            fun create(
                forrigeSteg: VilkårsvurdertKlage.Bekreftet.Avvist,
                fritekstTilBrev: String,
            ): Bekreftet {
                return Bekreftet(forrigeSteg, fritekstTilBrev)
            }
        }
    }
}

sealed class KunneIkkeLeggeTilFritekstForAvvist {
    object FantIkkeKlage : KunneIkkeLeggeTilFritekstForAvvist()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) :
        KunneIkkeLeggeTilFritekstForAvvist()
}
