package com.localmarts.pos.report;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

@Component
public class ReportSnapshotCodec {
    public record SnapshotV1(int snapshotVersion, String timezone, String currency, CashierReportSnapshot report) {}
    private final ObjectMapper mapper;
    public ReportSnapshotCodec(ObjectMapper mapper) { this.mapper = mapper; }
    public String encode(CashierReportSnapshot preview) {
        return mapper.writeValueAsString(new SnapshotV1(1, "Asia/Bangkok", "THB", preview));
    }
    public CashierReportSnapshot decode(Integer version, String json) {
        if (!Integer.valueOf(1).equals(version)) throw new ReportDataException("Unsupported saved report snapshot version: " + version + ".");
        try {
            if (mapper.readTree(json).path("snapshotVersion").asInt() != 1)
                throw new ReportDataException("Unsupported saved report snapshot version.");
            SnapshotV1 snapshot = mapper.readValue(json, SnapshotV1.class);
            if (!"Asia/Bangkok".equals(snapshot.timezone()) || !"THB".equals(snapshot.currency()) || snapshot.report() == null)
                throw new ReportDataException("Saved report snapshot is invalid.");
            return snapshot.report();
        } catch (JacksonException ex) {
            throw new ReportDataException("Saved report snapshot cannot be read.");
        }
    }
}
