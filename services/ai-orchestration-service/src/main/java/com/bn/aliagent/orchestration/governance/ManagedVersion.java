package com.bn.aliagent.orchestration.governance;

import java.util.UUID;

public record ManagedVersion(UUID id, VersionType type, String versionName, String status) { }
