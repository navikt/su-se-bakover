package no.nav.su.se.bakover.domain.regulering

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nyFradragperiode
import no.nav.su.se.bakover.test.nyReguleringssupplementFor
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragForPeriode
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.util.UUID

internal class OpprettEllerOppdaterReguleringKtTest {
    @Test
    fun `oppretter regulering fra søknadsbehandlingsvedtak`() {
        // TODO jah: Dette bør feile siden stønaden ikke har endret seg.
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre()).first
        sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement.empty(),
            BigDecimal(100),
        )
            .shouldBeRight()
    }

    // TODO - burde ha en som sjekker at perioden ikke matcher
    @Test
    fun `regulering skal bli automatisk behandlet, dersom vi ignorerer fradragstypen som fører til manuell`() {
        // TODO jah: Dette bør feile siden stønaden ikke har endret seg.
        val sakUtenÅpenBehandling = (
            iverksattSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragForPeriode(
                            fradragstype = Fradragstype.Alderspensjon,
                            månedsbeløp = 980.0,
                            periode = stønadsperiode2021.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            )
            ).first

        val supplementFor = nyReguleringssupplementFor(
            fnr = sakUtenÅpenBehandling.fnr,
            ReguleringssupplementFor.PerType(
                type = Fradragstype.Alderspensjon,
                fradragsperioder = nonEmptyListOf(nyFradragperiode()),
            ),
        )
        val actual = sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement(listOf(supplementFor)),
            BigDecimal(100),
        )
        actual.getOrFail().reguleringstype shouldBe Reguleringstype.AUTOMATISK
    }

    @Test
    fun `oppretter regulering fra revurdering`() {
        // TODO jah: Dette bør feile siden stønaden ikke har endret seg.
        val sakMedÅpenRevurdering = opprettetRevurdering().first
        sakMedÅpenRevurdering.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement.empty(),
            BigDecimal(100),
        )
            .shouldBeRight()
    }

    @Test
    fun `kan ikke regulere sak uten vedtak`() {
        val sakMedÅpenSøknadsbehandling = nySøknadsbehandlingMedStønadsperiode().first
        sakMedÅpenSøknadsbehandling.opprettEllerOppdaterRegulering(
            mai(2020),
            fixedClock,
            Reguleringssupplement.empty(),
            BigDecimal(100),
        )
            .shouldBe(
                Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left(),
            )
    }
}
