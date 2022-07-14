package db.migration

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteÅrsak
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class KonstruerVilkårOgGrunnlagTest {

    private val revurderingMedOverlappendeStønadsperioder = UUID.randomUUID()
    private val revurderingUtenOverlappIStønadsperioder = UUID.randomUUID()
    private val revurderingMedMangeStønadsperioder = UUID.randomUUID()

    @Test
    fun `mapping for overlappende grunnlag `() {
        KonstruerVilkårOgGrunnlag.forRevurderingOgRegulering(
            listOf(
                QueryRadRevurderingRegulering(
                    sakId = UUID.randomUUID(),
                    saksnummer = saksnummer,
                    vedtakId = UUID.randomUUID(),
                    revurderingId = revurderingMedOverlappendeStønadsperioder,
                    vedtakFom = 1.september(2021),
                    vedtakTom = 31.august(2022),
                    revurderingFom = 1.september(2021),
                    revurderingTom = 30.september(2022),
                    grunnlagFom = 1.september(2021),
                    grunnlagTom = 31.august(2022),
                    grunnlagOpprettet = Instant.parse("2022-02-18T11:50:47.772596Z"),
                    personligOppmøte = "IkkeMøttMenKortvarigSykMedLegeerklæring",
                ),
                QueryRadRevurderingRegulering(
                    sakId = UUID.randomUUID(),
                    saksnummer = saksnummer,
                    vedtakId = UUID.randomUUID(),
                    revurderingId = revurderingMedOverlappendeStønadsperioder,
                    vedtakFom = 1.oktober(2021),
                    vedtakTom = 30.september(2022),
                    revurderingFom = 1.september(2021),
                    revurderingTom = 30.september(2022),
                    grunnlagFom = 1.oktober(2021),
                    grunnlagTom = 30.september(2022),
                    grunnlagOpprettet = Instant.parse("2022-02-18T11:39:31.748728Z"),
                    personligOppmøte = "MøttPersonlig",
                ),
                QueryRadRevurderingRegulering(
                    sakId = UUID.randomUUID(),
                    saksnummer = saksnummer,
                    vedtakId = UUID.randomUUID(),
                    revurderingId = revurderingUtenOverlappIStønadsperioder,
                    vedtakFom = 1.januar(2022),
                    vedtakTom = 31.desember(2022),
                    revurderingFom = 1.januar(2022),
                    revurderingTom = 31.desember(2022),
                    grunnlagFom = 1.januar(2022),
                    grunnlagTom = 31.desember(2022),
                    grunnlagOpprettet = Instant.parse("2022-01-14T12:44:46.077191Z"),
                    personligOppmøte = "MøttPersonlig",
                ),
                QueryRadRevurderingRegulering(
                    sakId = UUID.randomUUID(),
                    saksnummer = saksnummer,
                    vedtakId = UUID.randomUUID(),
                    revurderingId = revurderingMedMangeStønadsperioder,
                    vedtakFom = 1.januar(2021),
                    vedtakTom = 31.desember(2021),
                    revurderingFom = 1.februar(2021),
                    revurderingTom = 31.desember(2022),
                    grunnlagFom = 1.februar(2021),
                    grunnlagTom = 31.desember(2021),
                    grunnlagOpprettet = Instant.parse("2022-04-27T17:34:07.655033Z"),
                    personligOppmøte = "MøttPersonlig",
                ),
                QueryRadRevurderingRegulering(
                    sakId = UUID.randomUUID(),
                    saksnummer = saksnummer,
                    vedtakId = UUID.randomUUID(),
                    revurderingId = revurderingMedMangeStønadsperioder,
                    vedtakFom = 1.januar(2022),
                    vedtakTom = 31.desember(2022),
                    revurderingFom = 1.februar(2021),
                    revurderingTom = 31.desember(2022),
                    grunnlagFom = 1.januar(2022),
                    grunnlagTom = 31.desember(2022),
                    grunnlagOpprettet = Instant.parse("2022-04-28T14:16:04.877175Z"),
                    personligOppmøte = "IkkeMøttMenVerge",
                ),
            ),
        ).also { vilkårPerRevurdering ->
            vilkårPerRevurdering shouldHaveSize 3
            vilkårPerRevurdering.single { it.behandlingInfo.id == revurderingMedOverlappendeStønadsperioder }
                .also { vilkår ->
                    vilkår.behandlingInfo shouldBe BehandlingInfo(
                        id = revurderingMedOverlappendeStønadsperioder,
                        periode = Periode.create(1.september(2021), 30.september(2022)),
                    )
                    vilkår.vilkår.vurderingsperioder shouldHaveSize 2
                    vilkår.vilkår.vurderingsperioder.first().also { vurderingsperiode ->
                        vurderingsperiode.periode shouldBe Periode.create(1.september(2021), 31.august(2022))
                        vurderingsperiode.vurdering shouldBe Vurdering.Innvilget
                        vurderingsperiode.grunnlag.also {
                            it.periode shouldBe Periode.create(1.september(2021), 31.august(2022))
                            it.årsak shouldBe PersonligOppmøteÅrsak.IkkeMøttMenKortvarigSykMedLegeerklæring
                        }
                    }
                    vilkår.vilkår.vurderingsperioder.last().also { vurderingsperiode ->
                        vurderingsperiode.periode shouldBe Periode.create(1.september(2022), 30.september(2022))
                        vurderingsperiode.vurdering shouldBe Vurdering.Innvilget
                        vurderingsperiode.grunnlag.also {
                            it.periode shouldBe Periode.create(1.september(2022), 30.september(2022))
                            it.årsak shouldBe PersonligOppmøteÅrsak.MøttPersonlig
                        }
                    }
                }
            vilkårPerRevurdering.single { it.behandlingInfo.id == revurderingUtenOverlappIStønadsperioder }
                .also { vilkår ->
                    vilkår.behandlingInfo shouldBe BehandlingInfo(
                        id = revurderingUtenOverlappIStønadsperioder,
                        periode = Periode.create(1.januar(2022), 31.desember(2022)),
                    )
                    vilkår.vilkår.vurderingsperioder.single().also { vurderingsperiode ->
                        vurderingsperiode.periode shouldBe Periode.create(1.januar(2022), 31.desember(2022))
                        vurderingsperiode.vurdering shouldBe Vurdering.Innvilget
                        vurderingsperiode.grunnlag.also {
                            it.periode shouldBe Periode.create(1.januar(2022), 31.desember(2022))
                            it.årsak shouldBe PersonligOppmøteÅrsak.MøttPersonlig
                        }
                    }
                }
            vilkårPerRevurdering.single { it.behandlingInfo.id == revurderingMedMangeStønadsperioder }
                .also { vilkår ->
                    vilkår.behandlingInfo shouldBe BehandlingInfo(
                        id = revurderingMedMangeStønadsperioder,
                        periode = Periode.create(1.februar(2021), 31.desember(2022)),
                    )
                    vilkår.vilkår.vurderingsperioder shouldHaveSize 2
                    vilkår.vilkår.vurderingsperioder.first().also { vurderingsperiode ->
                        vurderingsperiode.periode shouldBe Periode.create(1.februar(2021), 31.desember(2021))
                        vurderingsperiode.vurdering shouldBe Vurdering.Innvilget
                        vurderingsperiode.grunnlag.also {
                            it.periode shouldBe Periode.create(1.februar(2021), 31.desember(2021))
                            it.årsak shouldBe PersonligOppmøteÅrsak.MøttPersonlig
                        }
                    }
                    vilkår.vilkår.vurderingsperioder.last().also { vurderingsperiode ->
                        vurderingsperiode.periode shouldBe Periode.create(1.januar(2022), 31.desember(2022))
                        vurderingsperiode.vurdering shouldBe Vurdering.Innvilget
                        vurderingsperiode.grunnlag.also {
                            it.periode shouldBe Periode.create(1.januar(2022), 31.desember(2022))
                            it.årsak shouldBe PersonligOppmøteÅrsak.IkkeMøttMenVerge
                        }
                    }
                }
        }
    }
}
