package com.blog.system.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BackupStatusResponse(
        String backupDir,
        BackupFileResponse latestBackup,
        BackupDrillResponse latestDrill,
        List<BackupFileResponse> recentBackups,
        OffsetDateTime checkedAt
) {
}
