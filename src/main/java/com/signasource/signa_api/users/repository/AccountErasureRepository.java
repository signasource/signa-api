package com.signasource.signa_api.users.repository;

import com.signasource.signa_api.users.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Bulk-deletes the personal and behavioural data tied to a user. Purchase and gift records are
 * deliberately left out: they are financial records and stay, detached from any identity once the
 * {@code users} row is anonymised.
 */
@Repository
@RequiredArgsConstructor
public class AccountErasureRepository {

    private final EntityManager entityManager;

    public void deleteUserData(User user) {
        // Children first: attempts and progress rows must go before the rows they hang from.
        delete("LessonBlockAttempt", "user", user);
        delete("PracticeAttempt", "user", user);
        delete("UserLessonProgress", "user", user);
        delete("UserTopicProgress", "user", user);
        delete("UserCourseEnrollment", "user", user);
        delete("SignReport", "user", user);

        delete("UserAchievement", "user", user);
        delete("UserChallenge", "user", user);
        delete("UserDailyXp", "user", user);
        delete("UserLearnedSign", "user", user);
        delete("UserStats", "user", user);

        delete("UserDailyActivity", "user", user);
        delete("UserSettings", "user", user);
        delete("NotificationHistory", "user", user);
        delete("OrganizationMember", "user", user);

        delete("FriendEventLike", "user", user);
        entityManager
                .createQuery("delete from FriendEventLike l where l.eventOwnerId = :id")
                .setParameter("id", user.getId())
                .executeUpdate();
        // Invite codes bind to an email, not a user id, so they only match while it is still real.
        entityManager
                .createQuery("delete from InviteCode c where lower(c.email) = :email")
                .setParameter("email", user.getEmail().toLowerCase())
                .executeUpdate();
        entityManager
                .createNativeQuery(
                        "update purchase_seats set email = concat('erased-', cast(id as varchar(36)), '@invalid'), "
                                + "invitation_token = null "
                                + "where user_id = :id or lower(email) = :email")
                .setParameter("id", user.getId())
                .setParameter("email", user.getEmail().toLowerCase())
                .executeUpdate();
        entityManager
                .createQuery(
                        "delete from Friendship f where f.requester = :user or f.addressee = :user")
                .setParameter("user", user)
                .executeUpdate();
    }

    private void delete(String entity, String field, User user) {
        entityManager
                .createQuery("delete from " + entity + " e where e." + field + " = :user")
                .setParameter("user", user)
                .executeUpdate();
    }
}
