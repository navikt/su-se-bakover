package no.nav.su.se.bakover.service.statistikk

import BehandlingResultat
import BehandlingStatus
import Behandlingstype
import YtelseType
import behandling.klage.domain.Hjemmel
import behandling.revurdering.domain.Opphørsgrunn
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatnøkkel
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkVisningsrad
import no.nav.su.se.bakover.domain.statistikk.Statistikkoppløsning
import org.junit.jupiter.api.Test
import vilkår.common.domain.Avslagsgrunn
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class StatistikkVisningServiceTest {

    @Test
    fun `aggregerer gjeldende begrunnelser fra første utfall med eksplisitte nevnere`() {
        val rader = listOf(
            rad(
                behandlingType = Behandlingstype.SOKNAD,
                resultat = BehandlingResultat.AvslåttSøknadsbehandling,
                begrunnelse = "${Avslagsgrunn.FORMUE}, ${Avslagsgrunn.FORMUE}, FREMTIDIG_KODE",
            ),
            rad(
                behandlingType = Behandlingstype.SOKNAD,
                resultat = BehandlingResultat.AvslåttSøknadsbehandling,
                begrunnelse = null,
            ),
            rad(
                behandlingType = Behandlingstype.SOKNAD,
                resultat = BehandlingResultat.Avslag,
                begrunnelse = "Avslag på grunn av for tidlig søknad",
            ),
            rad(
                behandlingType = Behandlingstype.REVURDERING,
                resultat = BehandlingResultat.Opphør,
                begrunnelse = Opphørsgrunn.FOR_HØY_INNTEKT.name,
            ),
            rad(
                behandlingType = Behandlingstype.KLAGE,
                resultat = BehandlingResultat.AVVIST_KLAGE,
                begrunnelse = "IKKE_UNDERSKREVET",
            ),
            rad(
                behandlingType = Behandlingstype.KLAGE,
                status = BehandlingStatus.OversendtKlage,
                resultat = BehandlingResultat.DelvisOmgjøringKa,
                begrunnelse = "${Hjemmel.SU_PARAGRAF_3}, ${Hjemmel.FVL_PARAGRAF_31}",
            ),
            rad(
                behandlingType = Behandlingstype.KLAGE,
                resultat = BehandlingResultat.DelvisOmgjøringEgenVedtaksinstans,
                begrunnelse = Omgjøringsgrunn.NYE_OPPLYSNINGER.name,
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

        periode.utfall.sumOf { it.antall } shouldBe 7
        periode.avslagsgrunner.single().let {
            it.antallAvslag shouldBe 3
            it.antallUtenBegrunnelse shouldBe 1
            it.antallMedUkjentBegrunnelse shouldBe 1
            it.grunner.associateBy { grunn -> grunn.kode } shouldBe mapOf(
                Avslagsgrunn.FORMUE.name to SakStatistikkAvslagsgrunn(
                    kode = Avslagsgrunn.FORMUE.name,
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
            it["${SakStatistikkKategori.REVURDERING}-${BehandlingResultat.Opphør.value}"] shouldBe 1
            it["${SakStatistikkKategori.KLAGE}-${BehandlingResultat.AVVIST_KLAGE.value}"] shouldBe 1
        }
        periode.klagehjemler.single().let {
            it.resultat shouldBe BehandlingResultat.DelvisOmgjøringKa.value
            it.antallKlager shouldBe 1
            it.hjemler.map { hjemmel -> hjemmel.lov } shouldBe
                listOf(SakStatistikkLov.FVL, SakStatistikkLov.SU)
        }
        periode.klageomgjøringsgrunner.single().let {
            it.resultat shouldBe BehandlingResultat.DelvisOmgjøringEgenVedtaksinstans.value
            it.antallKlager shouldBe 1
            it.grunner.single().kode shouldBe Omgjøringsgrunn.NYE_OPPLYSNINGER.name
        }
    }

    @Test
    fun `normaliserer historiske resultatverdier`() {
        val periode = listOf(
            historiskRad(
                behandlingType = Behandlingstype.SOKNAD.name,
                status = BehandlingStatus.Avsluttet.value,
                resultat = "Feilregistrert",
            ),
            historiskRad(
                behandlingType = Behandlingstype.REVURDERING.name,
                resultat = "OpphørtRevurdering",
                begrunnelse = Opphørsgrunn.FOR_HØY_INNTEKT.name,
            ),
            historiskRad(
                behandlingType = Behandlingstype.KLAGE.name,
                resultat = BehandlingResultat.Avslag.value,
                begrunnelse = "IKKE_UNDERSKREVET",
            ),
        ).tilOppsummering(
            nøkkel = SakStatistikkAggregatnøkkel(
                fraOgMed = LocalDate.of(2025, 1, 1),
                tilOgMed = LocalDate.of(2025, 1, 31),
                oppløsning = Statistikkoppløsning.MÅNED,
            ),
            maksSekvensId = 3,
        ).perioder.single()

        periode.utfall.associate { "${it.behandlingskategori}-${it.resultat}" to it.antall } shouldBe mapOf(
            "${SakStatistikkKategori.KLAGE}-${BehandlingResultat.AVVIST_KLAGE.value}" to 1,
            "${SakStatistikkKategori.REVURDERING}-${BehandlingResultat.Opphør.value}" to 1,
            "${SakStatistikkKategori.SØKNAD}-${BehandlingResultat.Bortfalt.value}" to 1,
        )
    }

    @Test
    fun `senere utfall endrer ikke første begrunnelsesfordeling`() {
        val behandlingId = UUID.randomUUID()
        val oppsummering = listOf(
            rad(
                behandlingType = Behandlingstype.SOKNAD,
                resultat = BehandlingResultat.AvslåttSøknadsbehandling,
                begrunnelse = Avslagsgrunn.FORMUE.name,
                behandlingId = behandlingId,
                sekvensId = 1,
                dato = LocalDate.of(2025, 1, 15),
            ),
            rad(
                behandlingType = Behandlingstype.SOKNAD,
                status = BehandlingStatus.Avsluttet,
                resultat = BehandlingResultat.Avslag,
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
            it.grunner.single().kode shouldBe Avslagsgrunn.FORMUE.name
        }
        oppsummering.perioder.last().avslagsgrunner shouldBe emptyList()
    }

    @Test
    fun `grupperer kohorter etter mottattdato og ikke registreringsdato`() {
        val oppsummering = listOf(
            rad(
                behandlingType = Behandlingstype.REVURDERING,
                resultat = BehandlingResultat.Innvilget,
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
        behandlingType: Behandlingstype,
        status: BehandlingStatus = BehandlingStatus.Iverksatt,
        resultat: BehandlingResultat,
        begrunnelse: String?,
        behandlingId: UUID = UUID.randomUUID(),
        sekvensId: Long = behandlingId.mostSignificantBits.and(Long.MAX_VALUE),
        dato: LocalDate = LocalDate.of(2025, 1, 15),
        mottattDato: LocalDate = dato,
        registrertDato: LocalDate = dato,
    ): SakStatistikkVisningsrad = lagRad(
        behandlingType = behandlingType.name,
        status = status.value,
        resultat = resultat.value,
        begrunnelse = begrunnelse,
        behandlingId = behandlingId,
        sekvensId = sekvensId,
        dato = dato,
        mottattDato = mottattDato,
        registrertDato = registrertDato,
    )

    private fun historiskRad(
        behandlingType: String,
        status: String = BehandlingStatus.Iverksatt.value,
        resultat: String,
        begrunnelse: String? = null,
    ) = lagRad(
        behandlingType = behandlingType,
        status = status,
        resultat = resultat,
        begrunnelse = begrunnelse,
    )

    private fun lagRad(
        behandlingType: String,
        status: String,
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
            sakYtelse = YtelseType.SUUFORE.name,
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
