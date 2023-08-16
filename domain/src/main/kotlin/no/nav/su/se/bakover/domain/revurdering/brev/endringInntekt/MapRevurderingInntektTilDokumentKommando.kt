package no.nav.su.se.bakover.domain.revurdering.brev.endringInntekt

import arrow.core.getOrElse
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import java.lang.IllegalArgumentException

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
        fritekst = if (revurdering.skalSendeVedtaksbrev()) {
            revurdering.brevvalgRevurdering.skalSendeBrev()
                .getOrElse {
                    throw IllegalStateException("context mismatch: Revurderingen skal sende brev, men brevvalg skal ikke sendes. ${revurdering.id}")
                }.fritekst ?: ""
        } else {
            throw java.lang.IllegalStateException("Bedt om å generere brev for en revurdering som ikke skal sende brev. Saksnummer: ${revurdering.saksnummer} med revurderingid: ${revurdering.id}")
        },
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
