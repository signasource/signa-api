package com.signasource.signa_api.gamification.entity;

public enum ChallengeCriteriaType {
    COMPLETE_LESSONS(false),
    EARN_XP(false),
    PERFECT_LESSONS(false),
    /** Correct answers on camera-driven exercises, in lessons or free practice. */
    CAMERA_PRACTICES(false),
    /** Current streak length: reported as an absolute value, not as an increment. */
    STREAK_DAYS(true),
    /** Friend requests sent: asking is enough, so nobody is stuck waiting for someone to accept. */
    FRIEND_REQUESTS_SENT(false);

    private final boolean absolute;

    ChallengeCriteriaType(boolean absolute) {
        this.absolute = absolute;
    }

    /** Absolute criteria report the current total; the others report an amount to add. */
    public boolean isAbsolute() {
        return absolute;
    }
}
