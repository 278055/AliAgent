package com.bn.aliagent.knowledge.storage;

import java.util.UUID;

public final class ObjectKeyFactory {
    public String create(String tenantId, String originalFilename) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("缺少可信租户上下文");
        }
        String extension = extensionOf(originalFilename);
        return "knowledge/tenant-" + tenantId + "/" + UUID.randomUUID() + extension;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        if (index == -1 || index == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(index);
        return extension.matches("\\.[A-Za-z0-9]{1,16}") ? extension : "";
    }
}
