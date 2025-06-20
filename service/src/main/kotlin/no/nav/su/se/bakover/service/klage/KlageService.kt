package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
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
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.klage.brev.KunneIkkeLageBrevutkast
import java.util.UUID

interface KlageService {
    fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage>

    fun vilkårsvurder(command: VurderKlagevilkårCommand): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage>
    fun bekreftVilkårsvurderinger(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VilkårsvurdertKlage.Bekreftet>

    fun vurderOmOmgjøringEllerOpprettholdelse(request: KlageVurderingerRequest): Either<KunneIkkeVurdereKlage, VurdertKlage>
    fun bekreftVurderinger(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VurdertKlage.Bekreftet>

    fun leggTilAvvistFritekstTilBrev(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeLeggeTilFritekstForAvvist, AvvistKlage>

    fun sendTilAttestering(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeKlageTilAttestering, KlageTilAttestering>

    fun underkjenn(request: UnderkjennKlageRequest): Either<KunneIkkeUnderkjenneKlage, Klage>

    fun oversend(sakId: UUID, klageId: KlageId, attestant: NavIdentBruker.Attestant): Either<KunneIkkeOversendeKlage, OversendtKlage>

    fun iverksettAvvistKlage(
        klageId: KlageId,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteAvvistKlage, IverksattAvvistKlage>

    fun brevutkast(sakId: UUID, klageId: KlageId, ident: NavIdentBruker): Either<KunneIkkeLageBrevutkast, PdfA>

    fun avslutt(
        klageId: KlageId,
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
    ): Either<KunneIkkeAvslutteKlage, AvsluttetKlage>
}
