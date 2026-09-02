-- Achievements Seed
INSERT INTO achievements (id, code, title, description, icon_url, criteria_type, criteria_value, active)
VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'FIRST_LESSON', 'First Steps', 'Complete your first lesson.', 'https://via.placeholder.com/100?text=First', 'LESSONS_COMPLETED', 1, true),
    ('550e8400-e29b-41d4-a716-446655440002', 'COURSE_MASTER', 'Course Master', 'Complete an entire course.', 'https://via.placeholder.com/100?text=Master', 'COURSES_COMPLETED', 1, true),
    ('550e8400-e29b-41d4-a716-446655440003', 'STREAK_WEEK', 'Weekly Warrior', 'Maintain a 7-day learning streak.', 'https://via.placeholder.com/100?text=Warrior', 'STREAK_DAYS', 7, true),
    ('550e8400-e29b-41d4-a716-446655440004', 'STREAK_MONTH', 'Monthly Legend', 'Maintain a 30-day learning streak.', 'https://via.placeholder.com/100?text=Legend', 'STREAK_DAYS', 30, true),
    ('550e8400-e29b-41d4-a716-446655440005', 'XP_GRINDER', 'XP Grinder', 'Earn 1000 total XP.', 'https://via.placeholder.com/100?text=Grinder', 'TOTAL_XP', 1000, true),
    ('550e8400-e29b-41d4-a716-446655440006', 'CHALLENGE_CHAMPION', 'Challenge Champion', 'Complete 10 challenges.', 'https://via.placeholder.com/100?text=Champion', 'CHALLENGES_COMPLETED', 10, true),
    ('550e8400-e29b-41d4-a716-446655440007', 'GIFT_GIVER', 'Generous Soul', 'Send 5 gifts to friends.', 'https://via.placeholder.com/100?text=Giver', 'GIFTS_SENT', 5, true)
ON CONFLICT (code) DO NOTHING;
