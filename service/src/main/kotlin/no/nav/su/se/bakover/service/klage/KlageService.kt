package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevRequest
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import java.util.UUID

interface KlageService {
    fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage>

    fun vilkårsvurder(request: VurderKlagevilkårRequest): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage>
    fun bekreftVilkårsvurderinger(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VilkårsvurdertKlage.Bekreftet>

    fun vurder(request: KlageVurderingerRequest): Either<KunneIkkeVurdereKlage, VurdertKlage>
    fun bekreftVurderinger(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VurdertKlage.Bekreftet>

    fun leggTilAvvistFritekstTilBrev(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeLeggeTilFritekstForAvvist, AvvistKlage>

    fun sendTilAttestering(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering>

    fun underkjenn(request: UnderkjennKlageRequest): Either<KunneIkkeUnderkjenne, Klage>

    fun oversend(klageId: UUID, attestant: NavIdentBruker.Attestant): Either<KunneIkkeOversendeKlage, OversendtKlage>

    fun iverksettAvvistKlage(
        klageId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteAvvistKlage, IverksattAvvistKlage>

    fun brevutkast(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLageBrevutkast, ByteArray>

    fun avslutt(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
    ): Either<KunneIkkeAvslutteKlage, AvsluttetKlage>
}

sealed class KunneIkkeLageBrevutkast {
    object FantIkkeKlage : KunneIkkeLageBrevutkast()
    data class FeilVedBrevRequest(val feil: KunneIkkeLageBrevRequest) : KunneIkkeLageBrevutkast()
    data class GenereringAvBrevFeilet(val feil: KunneIkkeLageBrevForKlage) : KunneIkkeLageBrevutkast()
}
