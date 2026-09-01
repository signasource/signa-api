package com.signasource.signa_api.learning.repository.projection;

import com.signasource.signa_api.learning.entity.ProgressStatus;
import java.util.UUID;

public interface LessonProgressStatusView {

    UUID getLessonId();

    ProgressStatus getStatus();
}
