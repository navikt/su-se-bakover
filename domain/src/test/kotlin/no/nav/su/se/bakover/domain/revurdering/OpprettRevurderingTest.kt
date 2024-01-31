package no.nav.su.se.bakover.domain.revurdering

import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.enUkeEtterFixedClock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test

internal class OpprettRevurderingTest {

    @Test
    fun `kan opprette revurdering dersom det ikke finnes eksisterende åpne behandlinger`() {
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre(stønadsperiode = stønadsperiode2021)).first

        sakUtenÅpenBehandling.opprettRevurdering(
            command = OpprettRevurderingCommand(
                saksbehandler = saksbehandler,
                årsak = "MELDING_FRA_BRUKER",
                informasjonSomRevurderes = nonEmptyListOf(Revurderingsteg.Bosituasjon),
                periode = stønadsperiode2021.periode,
                sakId = sakUtenÅpenBehandling.id,
                begrunnelse = "begrunnelsen",
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    fun `kan opprette revurdering dersom det finnes en åpen revurdering`() {
        val sakMedÅpenRevurdering = opprettetRevurdering().first

        sakMedÅpenRevurdering.opprettRevurdering(
            command = OpprettRevurderingCommand(
                saksbehandler = saksbehandler,
                årsak = "MELDING_FRA_BRUKER",
                informasjonSomRevurderes = nonEmptyListOf(Revurderingsteg.Bosituasjon),
                periode = stønadsperiode2021.periode,
                sakId = sakMedÅpenRevurdering.id,
                begrunnelse = "begrunnelsen",
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    fun `kan opprette revurdering dersom det finnes en åpen regulering`() {
        val sakMedÅpenRegulering = innvilgetSøknadsbehandlingMedÅpenRegulering(mai(2021)).first
        sakMedÅpenRegulering.opprettRevurdering(
            command = OpprettRevurderingCommand(
                saksbehandler = saksbehandler,
                årsak = "MELDING_FRA_BRUKER",
                informasjonSomRevurderes = nonEmptyListOf(Revurderingsteg.Bosituasjon),
                periode = stønadsperiode2021.periode,
                sakId = sakMedÅpenRegulering.id,
                begrunnelse = "begrunnelsen",
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    fun `oppdatert tidspunkt er lik opprettet ved opprettelse - oppdatert skal være ulik opprettet ved oppdatering`() {
        val opprettetRevurdering = opprettetRevurdering().second
        opprettetRevurdering.opprettet shouldBe opprettetRevurdering.oppdatert

        val oppdatertRevurdering = opprettetRevurdering.oppdater(
            clock = enUkeEtterFixedClock,
            periode = opprettetRevurdering.periode,
            revurderingsårsak = Revurderingsårsak(
                årsak = Revurderingsårsak.Årsak.DØDSFALL,
                begrunnelse = Revurderingsårsak.Begrunnelse.create("Oppdaterer med ny årsak. Oppdatert tidspunktet skal være endret, og ikke lik opprettet"),
            ),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = opprettetRevurdering.grunnlagsdata,
                vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger,
            ),
            informasjonSomRevurderes = opprettetRevurdering.informasjonSomRevurderes,
            vedtakSomRevurderesMånedsvis = opprettetRevurdering.vedtakSomRevurderesMånedsvis,
            tilRevurdering = opprettetRevurdering.tilRevurdering,
            saksbehandler = opprettetRevurdering.saksbehandler,
        ).getOrFail()

        oppdatertRevurdering.oppdatert shouldNotBe oppdatertRevurdering.opprettet
        oppdatertRevurdering.oppdatert shouldBe Tidspunkt.now(enUkeEtterFixedClock)
        oppdatertRevurdering.opprettet shouldBe opprettetRevurdering.opprettet
    }
}
