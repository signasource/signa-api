package com.signasource.signa_api.organizations.repository.projection;

import java.util.UUID;

public interface VersionCountView {

    UUID getVersionId();

    long getTotal();
}
