package com.schwab.audit.dto;

import com.schwab.audit.model.AuditRecord;

import java.util.List;
import java.util.Map;

public class AuditExportBundle {
    private List<AuditRecord> targetRecords;
    private Map<Long, String> intermediateHashes;

    public AuditExportBundle() {}

    public AuditExportBundle(List<AuditRecord> targetRecords, Map<Long, String> intermediateHashes) {
        this.targetRecords = targetRecords;
        this.intermediateHashes = intermediateHashes;
    }

    public List<AuditRecord> getTargetRecords() {
        return targetRecords;
    }

    public void setTargetRecords(List<AuditRecord> targetRecords) {
        this.targetRecords = targetRecords;
    }

    public Map<Long, String> getIntermediateHashes() {
        return intermediateHashes;
    }

    public void setIntermediateHashes(Map<Long, String> intermediateHashes) {
        this.intermediateHashes = intermediateHashes;
    }
}
