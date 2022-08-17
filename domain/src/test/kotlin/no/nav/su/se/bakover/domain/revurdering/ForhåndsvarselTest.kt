package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class ForhåndsvarselTest {

    @Nested
    inner class `Overganger fra null` {

        @Test
        fun `Gyldig overgang fra null til SkalIkkeForhåndsvarsles`() {
            (null as Forhåndsvarsel?).prøvOvergangTilSkalIkkeForhåndsvarsles() shouldBe Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles.right()
        }

        @Test
        fun `Gyldig overgang fra null til Sendt`() {
            (null as Forhåndsvarsel?).prøvOvergangTilSendt() shouldBe Forhåndsvarsel.UnderBehandling.Sendt.right()
        }

        @Test
        fun `Ugyldig overgang fra null til FortsettMedSammeGrunnlag`() {
            (null as Forhåndsvarsel?).prøvOvergangTilFortsettMedSammeGrunnlag("b") shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Nothing::class,
                til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra null til EndreGrunnlaget`() {
            (null as Forhåndsvarsel?).prøvOvergangTilEndreGrunnlaget("c") shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Nothing::class,
                til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
            ).left()
        }

        @Test
        fun `kan lage forhåndsvarsel`() {
            simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(forhåndsvarsel = null).let { (_, simulert) ->
                simulert.lagForhåndsvarsel(
                    person = person(),
                    saksbehandlerNavn = "saks",
                    fritekst = "fri",
                    clock = fixedClock,
                ) shouldBe LagBrevRequest.Forhåndsvarsel(
                    person = person(),
                    saksbehandlerNavn = "saks",
                    fritekst = "fri",
                    dagensDato = LocalDate.now(fixedClock),
                    saksnummer = simulert.saksnummer,
                ).right()
            }
        }
    }

    @Nested
    inner class `Overganger fra SkalIkkeForhåndsvarsles` {

        @Test
        fun `Gyldig overgang fra SkalIkkeForhåndsvarsles til SkalIkkeForhåndsvarsles`() {
            Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles.prøvOvergangTilSkalIkkeForhåndsvarsles() shouldBe Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles.right()
        }

        @Test
        fun `Gyldig overgang fra SkalIkkeForhåndsvarsles til Sendt`() {
            Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles.prøvOvergangTilSendt() shouldBe Forhåndsvarsel.UnderBehandling.Sendt.right()
        }

        @Test
        fun `Ugyldig overgang fra SkalIkkeForhåndsvarsles til FortsettMedSammeGrunnlag`() {
            Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles.prøvOvergangTilFortsettMedSammeGrunnlag("e") shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles::class,
                til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra SkalIkkeForhåndsvarsles til EndreGrunnlaget`() {
            Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles.prøvOvergangTilEndreGrunnlaget("f") shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles::class,
                til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
            ).left()
        }

        @Test
        fun `kan lage forhåndsvarsel`() {
            simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles).let { (_, simulert) ->
                simulert.lagForhåndsvarsel(
                    person = person(),
                    saksbehandlerNavn = "saks",
                    fritekst = "fri",
                    clock = fixedClock,
                ) shouldBe LagBrevRequest.Forhåndsvarsel(
                    person = person(),
                    saksbehandlerNavn = "saks",
                    fritekst = "fri",
                    dagensDato = LocalDate.now(fixedClock),
                    saksnummer = simulert.saksnummer,
                ).right()
            }
        }
    }

    @Nested
    inner class `Overganger fra Sendt` {

        @Test
        fun `Ugyldig overgang fra Sendt til SkalIkkeForhåndsvarsles`() {
            Forhåndsvarsel.UnderBehandling.Sendt.prøvOvergangTilSkalIkkeForhåndsvarsles() shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.UnderBehandling.Sendt::class,
                til = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra Sendt til Sendt`() {
            Forhåndsvarsel.UnderBehandling.Sendt.prøvOvergangTilSendt() shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.UnderBehandling.Sendt::class,
                til = Forhåndsvarsel.UnderBehandling.Sendt::class,
            ).left()
        }

        @Test
        fun `Gyldig overgang fra Sendt til FortsettMedSammeGrunnlag`() {
            Forhåndsvarsel.UnderBehandling.Sendt.prøvOvergangTilFortsettMedSammeGrunnlag("h") shouldBe Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag(
                "h",
            ).right()
        }

        @Test
        fun `Gyldig overgang fra Sendt til EndreGrunnlaget`() {
            Forhåndsvarsel.UnderBehandling.Sendt.prøvOvergangTilEndreGrunnlaget("i") shouldBe Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget(
                "i",
            ).right()
        }

        @Test
        fun `kan ikke lage forhåndsvarsel`() {
            simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt).let { (_, simulert) ->
                simulert.lagForhåndsvarsel(
                    person = person(),
                    saksbehandlerNavn = "saks",
                    fritekst = "fri",
                    clock = fixedClock,
                ) shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                    Forhåndsvarsel.UnderBehandling.Sendt::class,
                    Forhåndsvarsel.UnderBehandling.Sendt::class,
                ).left()
            }
        }
    }

    @Nested
    inner class `Overganger fra Avsluttet` {

        @Test
        fun `Ugyldig overgang fra Avsluttet til SkalIkkeForhåndsvarsles`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet("j")
                .prøvOvergangTilSkalIkkeForhåndsvarsles() shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet::class,
                til = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra Avsluttet til Sendt`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet("k")
                .prøvOvergangTilSendt() shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet::class,
                til = Forhåndsvarsel.UnderBehandling.Sendt::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra Avsluttet til FortsettMedSammeGrunnlag`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet("n")
                .prøvOvergangTilFortsettMedSammeGrunnlag("o") shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet::class,
                til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra Avsluttet til EndreGrunnlaget`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet("p")
                .prøvOvergangTilEndreGrunnlaget("q") shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet::class,
                til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
            ).left()
        }
    }

    @Nested
    inner class `Overganger fra FortsettMedSammeGrunnlag` {

        @Test
        fun `Ugyldig overgang fra FortsettMedSammeGrunnlag til SkalIkkeForhåndsvarsles`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("r")
                .prøvOvergangTilSkalIkkeForhåndsvarsles() shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
                til = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra FortsettMedSammeGrunnlag til Sendt`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("s")
                .prøvOvergangTilSendt() shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
                til = Forhåndsvarsel.UnderBehandling.Sendt::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra FortsettMedSammeGrunnlag til FortsettMedSammeGrunnlag`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("v")
                .prøvOvergangTilFortsettMedSammeGrunnlag("w") shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
                til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra FortsettMedSammeGrunnlag til EndreGrunnlaget`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("x")
                .prøvOvergangTilEndreGrunnlaget("y") shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
                til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
            ).left()
        }
    }

    @Nested
    inner class `Overganger fra EndreGrunnlaget` {

        @Test
        fun `Ugyldig overgang fra EndreGrunnlaget til SkalIkkeForhåndsvarsles`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget("z")
                .prøvOvergangTilSkalIkkeForhåndsvarsles() shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
                til = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra EndreGrunnlaget til Sendt`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget("æ")
                .prøvOvergangTilSendt() shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
                til = Forhåndsvarsel.UnderBehandling.Sendt::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra EndreGrunnlaget til FortsettMedSammeGrunnlag`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget("aa")
                .prøvOvergangTilFortsettMedSammeGrunnlag("bb") shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
                til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
            ).left()
        }

        @Test
        fun `Ugyldig overgang fra EndreGrunnlaget til EndreGrunnlaget`() {
            Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget("cc")
                .prøvOvergangTilEndreGrunnlaget("dd") shouldBe Forhåndsvarsel.UgyldigTilstandsovergang(
                fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
                til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
            ).left()
        }
    }
}
