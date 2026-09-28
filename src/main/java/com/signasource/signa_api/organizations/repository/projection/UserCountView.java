package com.signasource.signa_api.organizations.repository.projection;

import java.util.UUID;

public interface UserCountView {

    UUID getUserId();

    long getTotal();
}
