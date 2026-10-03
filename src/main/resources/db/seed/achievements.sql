-- Achievements Seed
INSERT INTO achievements (id, code, title, description, icon_url, criteria_type, criteria_value, active, reward_streak_shields)
VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'FIRST_LESSON', 'First Steps', 'Complete your first lesson.', 'https://via.placeholder.com/100?text=First', 'LESSONS_COMPLETED', 1, true, 0),
    ('550e8400-e29b-41d4-a716-446655440002', 'COURSE_MASTER', 'Course Master', 'Complete an entire course.', 'https://via.placeholder.com/100?text=Master', 'COURSES_COMPLETED', 1, true, 0),
    ('550e8400-e29b-41d4-a716-446655440003', 'STREAK_WEEK', 'Una semana de racha', 'Llegá a 7 días de racha.', 'https://via.placeholder.com/100?text=Warrior', 'STREAK_DAYS', 7, true, 1),
    ('550e8400-e29b-41d4-a716-446655440004', 'STREAK_MONTH', 'Un mes de racha', 'Llegá a 30 días de racha.', 'https://via.placeholder.com/100?text=Legend', 'STREAK_DAYS', 30, true, 3),
    ('550e8400-e29b-41d4-a716-446655440005', 'XP_GRINDER', 'XP Grinder', 'Earn 1000 total XP.', 'https://via.placeholder.com/100?text=Grinder', 'TOTAL_XP', 1000, true, 0),
    ('550e8400-e29b-41d4-a716-446655440006', 'CHALLENGE_CHAMPION', 'Challenge Champion', 'Complete 10 challenges.', 'https://via.placeholder.com/100?text=Champion', 'CHALLENGES_COMPLETED', 10, true, 0),
    ('550e8400-e29b-41d4-a716-446655440007', 'GIFT_GIVER', 'Generous Soul', 'Send 5 gifts to friends.', 'https://via.placeholder.com/100?text=Giver', 'GIFTS_SENT', 5, true, 0),
    ('550e8400-e29b-41d4-a716-446655440008', 'STREAK_3', 'Tres días de racha', 'Llegá a 3 días de racha.', NULL, 'STREAK_DAYS', 3, true, 1),
    ('550e8400-e29b-41d4-a716-446655440009', 'STREAK_14', 'Dos semanas de racha', 'Llegá a 14 días de racha.', NULL, 'STREAK_DAYS', 14, true, 2),
    ('550e8400-e29b-41d4-a716-44665544000a', 'STREAK_60', 'Dos meses de racha', 'Llegá a 60 días de racha.', NULL, 'STREAK_DAYS', 60, true, 4),
    ('550e8400-e29b-41d4-a716-44665544000b', 'STREAK_100', 'Cien días de racha', 'Llegá a 100 días de racha.', NULL, 'STREAK_DAYS', 100, true, 5)
ON CONFLICT (code) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    criteria_type = EXCLUDED.criteria_type,
    criteria_value = EXCLUDED.criteria_value,
    reward_streak_shields = EXCLUDED.reward_streak_shields;
