package no.nav.su.se.bakover.domain.revurdering

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.oppdater.oppdaterRevurdering
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test

internal class OppdaterRevurderingTest {
    @Test
    fun `Omgjøring i oppdater krever en omgjøringsgrunn`() {
        val (sakMedÅpenRevurdering, revurdering) = opprettetRevurdering()
        sakMedÅpenRevurdering.oppdaterRevurdering(
            command = OppdaterRevurderingCommand(
                revurderingId = revurdering.id,
                periode = år(2021),
                årsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK.name,
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
            clock = fixedClock,
        ).shouldBeLeft().let {
            it shouldBe KunneIkkeOppdatereRevurdering.MåhaOmgjøringsgrunn
        }
    }

    @Test
    fun `Omgjøring i oppdater med omgjøringsgrunn har det i obj`() {
        val (sakMedÅpenRevurdering, revurdering) = opprettetRevurdering()

        sakMedÅpenRevurdering.oppdaterRevurdering(
            command = OppdaterRevurderingCommand(
                revurderingId = revurdering.id,
                periode = år(2021),
                årsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK.name,
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                omgjøringsgrunn = Omgjøringsgrunn.NYE_OPPLYSNINGER.name,
            ),
            clock = fixedClock,
        ).shouldBeRight().let {
            it.omgjøringsgrunn shouldBe Omgjøringsgrunn.NYE_OPPLYSNINGER
            it.revurderingsårsak.årsak shouldBe Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK
        }
    }
}
