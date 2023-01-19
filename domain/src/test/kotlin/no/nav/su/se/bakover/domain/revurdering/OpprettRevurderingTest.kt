package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.periode.september
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.revurdering.avkorting.KanIkkeRevurderePgaAvkorting
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.oppdater.oppdaterRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.enUkeEtterFixedClock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingsårsak
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.stønadsperiode2022
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
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
    fun `kan ikke opprette revurdering dersom det finnes en åpen revurdering`() {
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
        ) shouldBe KunneIkkeOppretteRevurdering.HarÅpenBehandling.left()
    }

    @Test
    fun `kan ikke opprette revurdering dersom det finnes en åpen regulering`() {
        val sakMedÅpenRegulering = innvilgetSøknadsbehandlingMedÅpenRegulering(1.mai(2021)).first
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
        ) shouldBe KunneIkkeOppretteRevurdering.HarÅpenBehandling.left()
    }

    @Test
    fun `får ikke opprettet revurdering for periode med avkortingsvarsel som er påbegynt avkortet i ny stønadsperiode`() {
        val clock = tikkendeFixedClock()

        // opprett nytt avkortingsvarsel
        val (sak, _) = vedtakRevurdering(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = mai(2021)..desember(2021),
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    periode = mai(2021)..desember(2021),
                ),
            ),
            utbetalingerKjørtTilOgMed = oktober(2021).fraOgMed,
            clock = clock,
        ).shouldBeType<Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering>>().also {
            it.second.behandling.avkorting.shouldBeType<AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel>()
                .also { nyttVarsel ->
                    nyttVarsel.periode() shouldBe mai(2021)..september(2021)
                }
        }

        // konsumer avkortingsvarsel og start avkorting i ny stønadsperiode
        val (sak2, søknadsbehandlingMedPåbegyntAvkorting) = iverksattSøknadsbehandlingUføre(
            stønadsperiode = stønadsperiode2022,
            sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                sakId = sak.id,
                søknadInnhold = søknadinnholdUføre(),
            ),
            clock = clock,
        ).let { it.first to it.third }
            .shouldBeType<Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling>>().also {
                it.second.behandling.avkorting.shouldBeType<AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående>()
                    .also {
                        it.avkortingsvarsel.periode() shouldBe mai(2021)..september(2021)
                    }
            }

        // ny revurdering
        val (sak3, nyRevurdering) = opprettetRevurdering(
            revurderingsperiode = januar(2021),
            sakOgVedtakSomKanRevurderes = sak2 to søknadsbehandlingMedPåbegyntAvkorting,
            clock = clock,
        ).also {
            it.second.shouldBeType<OpprettetRevurdering>().periode shouldBe januar(2021)
        }

        /**
         * Får ikke overlappe med den opprinnelige perioden hvor avkortingsvarselet ble opprettet (mai-des)
         * med mindre hele perioden hvor vaselet opppsto og konsumeres revurderes.
         */

        listOf(
            mai(2021),
            mai(2021)..september(2021),
            mai(2021)..desember(2021),
        ).forEach { ulovligPeriode ->
            sak3.oppdaterRevurdering(
                OppdaterRevurderingCommand(
                    revurderingId = nyRevurdering.id,
                    periode = ulovligPeriode,
                    årsak = revurderingsårsak.årsak.toString(),
                    begrunnelse = revurderingsårsak.begrunnelse.toString(),
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.PersonligOppmøte),
                ),
                clock = clock,
            ) shouldBe KunneIkkeOppdatereRevurdering.Avkorting(
                KanIkkeRevurderePgaAvkorting.PågåendeAvkortingForPeriode(
                    periode = ulovligPeriode,
                    vedtakId = søknadsbehandlingMedPåbegyntAvkorting.id,
                ),
            ).left()
        }

        listOf(
            mai(2021)..mai(2022),
            januar(2022)..mars(2022),
            mai(2022),
        ).forEach { lovligPeriode ->
            sak3.oppdaterRevurdering(
                OppdaterRevurderingCommand(
                    revurderingId = nyRevurdering.id,
                    periode = lovligPeriode,
                    saksbehandler = saksbehandler,
                    årsak = revurderingsårsak.årsak.toString(),
                    begrunnelse = revurderingsårsak.begrunnelse.toString(),
                    informasjonSomRevurderes = listOf(Revurderingsteg.PersonligOppmøte),
                ),
                clock = clock,
            ).getOrFail().also {
                it.periode shouldBe lovligPeriode
            }
        }
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
            grunnlagsdata = opprettetRevurdering.grunnlagsdata,
            vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger,
            informasjonSomRevurderes = opprettetRevurdering.informasjonSomRevurderes,
            vedtakSomRevurderesMånedsvis = opprettetRevurdering.vedtakSomRevurderesMånedsvis,
            tilRevurdering = opprettetRevurdering.tilRevurdering,
            avkorting = opprettetRevurdering.avkorting,
            saksbehandler = opprettetRevurdering.saksbehandler,
        ).getOrFail()

        oppdatertRevurdering.oppdatert shouldNotBe oppdatertRevurdering.opprettet
        oppdatertRevurdering.oppdatert shouldBe Tidspunkt.now(enUkeEtterFixedClock)
        oppdatertRevurdering.opprettet shouldBe opprettetRevurdering.opprettet
    }
}
