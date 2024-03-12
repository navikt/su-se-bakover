package no.nav.su.se.bakover.domain.revurdering.brev.endringInntekt

import beregning.domain.Beregning
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import satser.domain.SatsFactory
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.harEPS
import vilkår.vurderinger.domain.harForventetInntektStørreEnn0

/**
 * Ment for internt bruk innenfor revurdering/brev pakken.
 *
 * @throws IllegalArgumentException dersom man ikke skulle sende brev.
 */
internal fun lagRevurderingInntektDokumentKommando(
    revurdering: Revurdering,
    beregning: Beregning,
    satsFactory: SatsFactory,
): IverksettRevurderingDokumentCommand.Inntekt {
    return IverksettRevurderingDokumentCommand.Inntekt(
        fødselsnummer = revurdering.fnr,
        saksnummer = revurdering.saksnummer,
        saksbehandler = revurdering.saksbehandler,
        attestant = revurdering.hentAttestantSomIverksatte(),
        beregning = beregning,
        fritekst = revurdering.brevvalgRevurdering.skalSendeBrev().getOrNull()?.fritekst,
        // TODO("flere_satser denne må endres til å støtte flere")
        harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEPS(),
        forventetInntektStørreEnn0 = revurdering.vilkårsvurderinger.uføreVilkår()
            .fold(
                {
                    TODO("vilkårsvurdering_alder brev for alder er ikke implementert enda")
                },
                {
                    it.grunnlag.harForventetInntektStørreEnn0()
                },
            ),
        satsoversikt = Satsoversikt.fra(revurdering, satsFactory),
    )
}
