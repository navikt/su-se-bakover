package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatnøkkel
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkVisningsrad
import no.nav.su.se.bakover.domain.statistikk.Statistikkoppløsning
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class StatistikkVisningServiceTest {

    @Test
    fun `aggregerer begrunnelser fra første utfall med eksplisitte nevnere`() {
        val rader = listOf(
            rad(
                behandlingType = "SOKNAD",
                resultat = "AVSLÅTT",
                begrunnelse = "FORMUE, FORMUE, FREMTIDIG_KODE",
            ),
            rad(
                behandlingType = "SOKNAD",
                resultat = "AVSLÅTT",
                begrunnelse = null,
            ),
            rad(
                behandlingType = "SOKNAD",
                resultat = "AVSLAG",
                begrunnelse = "Avslag på grunn av for tidlig søknad",
            ),
            rad(
                behandlingType = "SOKNAD",
                status = "AVSLUTTET",
                resultat = "Feilregistrert",
                begrunnelse = null,
            ),
            rad(
                behandlingType = "REVURDERING",
                resultat = "OpphørtRevurdering",
                begrunnelse = "FOR_HØY_INNTEKT",
            ),
            rad(
                behandlingType = "KLAGE",
                resultat = "AVSLAG",
                begrunnelse = "IKKE_UNDERSKREVET",
            ),
            rad(
                behandlingType = "KLAGE",
                status = "OVERSENDT",
                resultat = "DELVIS_OMGJØRING",
                begrunnelse = "SU_PARAGRAF_3, FVL_PARAGRAF_31",
            ),
            rad(
                behandlingType = "KLAGE",
                resultat = "DELVIS_OMGJØRING",
                begrunnelse = "NYE_OPPLYSNINGER",
            ),
        )

        val periode = rader.tilOppsummering(
            nøkkel = SakStatistikkAggregatnøkkel(
                fraOgMed = LocalDate.of(2025, 1, 1),
                tilOgMed = LocalDate.of(2025, 1, 31),
                oppløsning = Statistikkoppløsning.MÅNED,
            ),
            maksSekvensId = rader.maxOf { it.sekvensId },
        ).perioder.single()

        periode.utfall.sumOf { it.antall } shouldBe 8
        periode.avslagsgrunner.single().let {
            it.antallAvslag shouldBe 3
            it.antallUtenBegrunnelse shouldBe 1
            it.antallMedUkjentBegrunnelse shouldBe 1
            it.grunner.associateBy { grunn -> grunn.kode } shouldBe mapOf(
                "FORMUE" to SakStatistikkAvslagsgrunn(
                    kode = "FORMUE",
                    paragrafer = listOf(SakStatistikkParagraf(SakStatistikkLov.SU, 8)),
                    antallBehandlinger = 1,
                ),
                "FOR_TIDLIG_SØKNAD" to SakStatistikkAvslagsgrunn(
                    kode = "FOR_TIDLIG_SØKNAD",
                    paragrafer = emptyList(),
                    antallBehandlinger = 1,
                ),
            )
        }
        periode.opphørsgrunner.single().antallOpphør shouldBe 1
        periode.klageavvisningsgrunner.single().antallAvvisteKlager shouldBe 1
        periode.utfall.associate { "${it.behandlingskategori}-${it.resultat}" to it.antall }.let {
            it["REVURDERING-OPPHØRT"] shouldBe 1
            it["KLAGE-AVVIST"] shouldBe 1
            it["SØKNAD-BORTFALT"] shouldBe 1
        }
        periode.klagehjemler.single().let {
            it.resultat shouldBe "DELVIS_OMGJØRING"
            it.antallKlager shouldBe 1
            it.hjemler.map { hjemmel -> hjemmel.lov } shouldBe
                listOf(SakStatistikkLov.FVL, SakStatistikkLov.SU)
        }
        periode.klageomgjøringsgrunner.single().let {
            it.resultat shouldBe "DELVIS_OMGJØRING"
            it.antallKlager shouldBe 1
            it.grunner.single().kode shouldBe "NYE_OPPLYSNINGER"
        }
    }

    @Test
    fun `senere utfall endrer ikke historisk begrunnelsesfordeling`() {
        val behandlingId = UUID.randomUUID()
        val oppsummering = listOf(
            rad(
                behandlingType = "SOKNAD",
                resultat = "AVSLÅTT",
                begrunnelse = "FORMUE",
                behandlingId = behandlingId,
                sekvensId = 1,
                dato = LocalDate.of(2025, 1, 15),
            ),
            rad(
                behandlingType = "SOKNAD",
                status = "AVSLUTTET",
                resultat = "AVSLAG",
                begrunnelse = "FREMTIDIG_KODE",
                behandlingId = behandlingId,
                sekvensId = 2,
                dato = LocalDate.of(2025, 2, 15),
            ),
        ).tilOppsummering(
            nøkkel = SakStatistikkAggregatnøkkel(
                fraOgMed = LocalDate.of(2025, 1, 1),
                tilOgMed = LocalDate.of(2025, 2, 28),
                oppløsning = Statistikkoppløsning.MÅNED,
            ),
            maksSekvensId = 2,
        )

        oppsummering.metadata.behandlingerMedFlereUtfall shouldBe 1
        oppsummering.perioder.first().avslagsgrunner.single().let {
            it.antallAvslag shouldBe 1
            it.antallMedUkjentBegrunnelse shouldBe 0
            it.grunner.single().kode shouldBe "FORMUE"
        }
        oppsummering.perioder.last().avslagsgrunner shouldBe emptyList()
    }

    @Test
    fun `grupperer kohorter etter mottattdato og ikke registreringsdato`() {
        val oppsummering = listOf(
            rad(
                behandlingType = "REVURDERING",
                resultat = "INNVILGET",
                begrunnelse = null,
                mottattDato = LocalDate.of(2025, 1, 31),
                registrertDato = LocalDate.of(2025, 2, 1),
                dato = LocalDate.of(2025, 2, 15),
            ),
        ).tilOppsummering(
            nøkkel = SakStatistikkAggregatnøkkel(
                fraOgMed = LocalDate.of(2025, 1, 1),
                tilOgMed = LocalDate.of(2025, 2, 28),
                oppløsning = Statistikkoppløsning.MÅNED,
            ),
            maksSekvensId = 1,
        )

        oppsummering.kohorter.single().let {
            it.fraOgMed shouldBe LocalDate.of(2025, 1, 1)
            it.tilOgMed shouldBe LocalDate.of(2025, 1, 31)
            it.antallStartet shouldBe 1
        }
        oppsummering.perioder.first().antall shouldBe emptyList()
        oppsummering.perioder.last().antall.single().antall shouldBe 1
    }

    private fun rad(
        behandlingType: String,
        status: String = "IVERKSATT",
        resultat: String,
        begrunnelse: String?,
        behandlingId: UUID = UUID.randomUUID(),
        sekvensId: Long = behandlingId.mostSignificantBits.and(Long.MAX_VALUE),
        dato: LocalDate = LocalDate.of(2025, 1, 15),
        mottattDato: LocalDate = dato,
        registrertDato: LocalDate = dato,
    ): SakStatistikkVisningsrad {
        val tidspunkt = Tidspunkt.create(dato.atStartOfDay().toInstant(ZoneOffset.UTC))
        val mottattTidspunkt = Tidspunkt.create(mottattDato.atStartOfDay().toInstant(ZoneOffset.UTC))
        val registrertTidspunkt = Tidspunkt.create(registrertDato.atStartOfDay().toInstant(ZoneOffset.UTC))
        return SakStatistikkVisningsrad(
            sekvensId = sekvensId,
            behandlingId = behandlingId,
            sakYtelse = "SU_UFØR",
            behandlingType = behandlingType,
            behandlingAarsak = null,
            behandlingStatus = status,
            behandlingResultat = resultat,
            resultatBegrunnelse = begrunnelse,
            mottattTid = mottattTidspunkt,
            registrertTid = registrertTidspunkt,
            funksjonellTid = tidspunkt,
            tekniskTid = tidspunkt,
            revurderingstype = null,
        )
    }
}
