package no.nav.su.se.bakover.domain.søknadsbehandling

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.test.beregnetSøknadsbehandling
import no.nav.su.se.bakover.test.grunnlagsdataMedEpsMedFradrag
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.skatt.nySøknadsbehandlingMedSkattegrunnlag
import no.nav.su.se.bakover.test.søknadsbehandlingTrukket
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.tilAttesteringSøknadsbehandling
import no.nav.su.se.bakover.test.underkjentSøknadsbehandling
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SøknadsbehandlingMedSkattegrunnlagTest {

    @Nested
    inner class KanIkkeOppfriske {
        @Test
        fun `tilstand tilAttestering`() {
            nySøknadsbehandlingMedSkattegrunnlag(
                søknadsbehandling = tilAttesteringSøknadsbehandling().second,
            ).hentNySkattedata { _, _ -> nySkattegrunnlag() }.shouldBeLeft()
        }

        @Test
        fun `tilstand iverksatt`() {
            // copy internal støtter ikke å legge til skattereferanser fra denne tilstanden
            assertThrows<UnsupportedOperationException> {
                nySøknadsbehandlingMedSkattegrunnlag(
                    søknadsbehandling = iverksattSøknadsbehandling().second,
                ).hentNySkattedata { _, _ -> nySkattegrunnlag() }
            }
        }

        @Test
        fun `tilstand lukket`() {
            // copy internal støtter ikke å legge til skattereferanser fra denne tilstanden
            assertThrows<UnsupportedOperationException> {
                nySøknadsbehandlingMedSkattegrunnlag(
                    søknadsbehandling = søknadsbehandlingTrukket().second,
                ).hentNySkattedata { _, _ -> nySkattegrunnlag() }
            }
        }
    }

    @Nested
    inner class KanOppfriske {
        @Test
        fun `tilstand vilkårsvurdert med eps`() {
            nySøknadsbehandlingMedSkattegrunnlag(
                eps = nySkattegrunnlag(),
                søknadsbehandling = søknadsbehandlingVilkårsvurdertInnvilget(
                    grunnlagsdata = grunnlagsdataMedEpsMedFradrag(),
                ).second,
            ).hentNySkattedata { _, _ -> nySkattegrunnlag() }.shouldBeRight()
        }

        @Test
        fun `tilstand beregnet`() {
            nySøknadsbehandlingMedSkattegrunnlag(
                søknadsbehandling = beregnetSøknadsbehandling().second,
            ).hentNySkattedata { _, _ -> nySkattegrunnlag() }.shouldBeRight()
        }

        @Test
        fun `tilstand simulert`() {
            nySøknadsbehandlingMedSkattegrunnlag(
                søknadsbehandling = simulertSøknadsbehandling().second,
            ).hentNySkattedata { _, _ -> nySkattegrunnlag() }.shouldBeRight()
        }

        @Test
        fun `tilstand underkjent`() {
            nySøknadsbehandlingMedSkattegrunnlag(
                søknadsbehandling = underkjentSøknadsbehandling().second,
            ).hentNySkattedata { _, _ -> nySkattegrunnlag() }.shouldBeRight()
        }
    }
}
