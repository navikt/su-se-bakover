package db.migration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import no.nav.su.se.bakover.common.Tidspunkt;
import no.nav.su.se.bakover.common.periode.Periode;
import no.nav.su.se.bakover.database.beregning.BeregningMapperKt;
import no.nav.su.se.bakover.domain.beregning.Beregning;
import no.nav.su.se.bakover.domain.beregning.BeregningFactory;
import no.nav.su.se.bakover.domain.beregning.Sats;
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag;
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory;
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

public class V42__store_beregning_as_json_and_patch_existing extends BaseJavaMigration {
    private ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new KotlinModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
            .build();

    @Override
    public void migrate(Context context) throws Exception {
        Statement statement = context.getConnection().createStatement();
        statement.execute("alter table behandling add column if not exists beregning jsonb");

        ResultSet rs = statement.executeQuery("select b.behandlingId, b.fom, b.tom, b.sats, f.fradragstype, f.beløp, f.utenlandskinntekt from beregning b left outer join fradrag f on b.id = f.beregningId;");
        List<BeregningData> beregninger = new ArrayList<>();
        List<FradragData> fradrag = new ArrayList<>();

        while (rs.next()) {
            String behandlingId = rs.getString("behandlingId");
            if (beregninger.stream().noneMatch(beregningData -> beregningData.behandlingid.equals(behandlingId))) {
                beregninger.add(new BeregningData(
                        behandlingId,
                        rs.getDate("fom"),
                        rs.getDate("tom"),
                        Sats.valueOf(rs.getString("sats"))
                ));
            }
            String fradragstype = rs.getString("fradragstype");
            if (fradragstype != null) {
                fradrag.add(new FradragData(
                        behandlingId,
                        fradragstype,
                        rs.getInt("beløp"),
                        mapper.readValue(rs.getString("utenlandskinntekt"), UtenlandskInntekt.class)
                ));
            }
        }
        rs.close();

        Map<String, Beregning> nyeBeregniger = new HashMap<>();
        for (BeregningData beregning : beregninger) {
            List<FradragData> fradragForBeregning = fradrag.stream()
                    .filter(fradragData -> fradragData.behandlingId.equals(beregning.behandlingid))
                    .collect(Collectors.toList());
            Periode periode = new Periode(beregning.fom.toLocalDate(), beregning.tom.toLocalDate());
            if (fradragForBeregning.isEmpty()) {
                nyeBeregniger.put(beregning.behandlingid, BeregningFactory.INSTANCE.ny(
                        UUID.randomUUID(),
                        Tidspunkt.Companion.now(Clock.systemUTC()),
                        periode,
                        beregning.sats,
                        Collections.emptyList()));
            } else {
                List<Fradrag> nyeFradrag = new ArrayList<>();
                for (FradragData f : fradragForBeregning) {
                    no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt utenlandskInntekt = null;
                    if (f.utenlandskinntekt != null) {
                        utenlandskInntekt = new no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt(
                                f.utenlandskinntekt.beløpIUtenlandskValuta,
                                f.utenlandskinntekt.valuta,
                                f.utenlandskinntekt.kurs);
                    }

                    nyeFradrag.add(FradragFactory.INSTANCE.ny(
                            Fradragstype.valueOf(f.fradragstype),
                            f.beløp,
                            periode,
                            utenlandskInntekt
                    ));
                }

                nyeBeregniger.put(beregning.behandlingid, BeregningFactory.INSTANCE.ny(
                        UUID.randomUUID(),
                        Tidspunkt.Companion.now(Clock.systemUTC()),
                        periode,
                        beregning.sats,
                        nyeFradrag));
            }
        }

        for (Map.Entry<String, Beregning> entry : nyeBeregniger.entrySet()) {
            PreparedStatement ps = context.getConnection().prepareStatement("update behandling set beregning = to_json(?::json) where id = ?");
            ps.setString(1, mapper.writeValueAsString(BeregningMapperKt.toSnapshot(entry.getValue())));
            ps.setObject(2, UUID.fromString(entry.getKey()));
            ps.execute();
            ps.close();
        }
        statement.close();
    }

    private class BeregningData {
        public String behandlingid;
        public Date fom;
        public Date tom;
        public Sats sats;

        @Override
        public String toString() {
            return "BeregningData{" +
                    "behandlingid='" + behandlingid + '\'' +
                    ", fom=" + fom +
                    ", tom=" + tom +
                    ", sats=" + sats +
                    '}';
        }

        BeregningData(String behandlingid, Date fom, Date tom, Sats sats) {
            this.behandlingid = behandlingid;
            this.fom = fom;
            this.tom = tom;
            this.sats = sats;
        }
    }

    private class FradragData {
        public String behandlingId;
        public String fradragstype;
        public int beløp;
        public V42__store_beregning_as_json_and_patch_existing.UtenlandskInntekt utenlandskinntekt;

        FradragData(String behandlingId, String fradragstype, int beløp, V42__store_beregning_as_json_and_patch_existing.UtenlandskInntekt utenlandskinntekt) {
            this.behandlingId = behandlingId;
            this.fradragstype = fradragstype;
            this.beløp = beløp;
            this.utenlandskinntekt = utenlandskinntekt;
        }

        @Override
        public String toString() {
            return "FradragData{" +
                    "fradragstype='" + fradragstype + '\'' +
                    ", beløp=" + beløp +
                    ", utenlandskinntekt='" + utenlandskinntekt + '\'' +
                    '}';
        }
    }

    private static class UtenlandskInntekt {
        public int beløpIUtenlandskValuta;
        public String valuta;
        public double kurs;
        public boolean valid;

        public UtenlandskInntekt() {
        }

        UtenlandskInntekt(int beløpIUtenlandskValuta, String valuta, double kurs, boolean valid) {
            this.beløpIUtenlandskValuta = beløpIUtenlandskValuta;
            this.valuta = valuta;
            this.kurs = kurs;
            this.valid = valid;
        }

        public int getBeløpIUtenlandskValuta() {
            return beløpIUtenlandskValuta;
        }

        public void setBeløpIUtenlandskValuta(int beløpIUtenlandskValuta) {
            this.beløpIUtenlandskValuta = beløpIUtenlandskValuta;
        }

        public String getValuta() {
            return valuta;
        }

        public void setValuta(String valuta) {
            this.valuta = valuta;
        }

        public double getKurs() {
            return kurs;
        }

        public void setKurs(double kurs) {
            this.kurs = kurs;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }
    }
}
