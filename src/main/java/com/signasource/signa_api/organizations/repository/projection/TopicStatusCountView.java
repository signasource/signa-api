package com.signasource.signa_api.organizations.repository.projection;

import com.signasource.signa_api.learning.entity.ProgressStatus;
import java.util.UUID;

public interface TopicStatusCountView {

    UUID getTopicId();

    ProgressStatus getStatus();

    long getUsers();
}
