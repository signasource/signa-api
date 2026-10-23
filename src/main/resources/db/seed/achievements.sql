-- Achievements Seed
-- Titles and descriptions are UI copy (Spanish). Rewards are credited when the achievement is earned.
INSERT INTO achievements (id, code, title, description, icon_url, criteria_type, criteria_value, active, reward_streak_shields, reward_gems)
VALUES
    -- Lecciones y cursos
    ('550e8400-e29b-41d4-a716-446655440001', 'FIRST_LESSON', 'Primera lección', 'Completá tu primera lección.', 'https://via.placeholder.com/100?text=First', 'LESSONS_COMPLETED', 1, true, 0, 10),
    ('550e8400-e29b-41d4-a716-446655440011', 'LESSONS_25', 'Alumno dedicado', 'Completá 25 lecciones.', NULL, 'LESSONS_COMPLETED', 25, true, 0, 30),
    ('550e8400-e29b-41d4-a716-446655440012', 'LESSONS_50', 'Estudiante avanzado', 'Completá 50 lecciones.', NULL, 'LESSONS_COMPLETED', 50, true, 0, 50),
    ('550e8400-e29b-41d4-a716-446655440013', 'LESSONS_100', 'Veterano', 'Completá 100 lecciones.', NULL, 'LESSONS_COMPLETED', 100, true, 0, 100),
    ('550e8400-e29b-41d4-a716-446655440002', 'COURSE_MASTER', 'Primer curso', 'Completá un curso.', 'https://via.placeholder.com/100?text=Master', 'COURSES_COMPLETED', 1, true, 0, 50),
    ('550e8400-e29b-41d4-a716-446655440014', 'COURSES_3', 'Formación continua', 'Completá 3 cursos.', NULL, 'COURSES_COMPLETED', 3, true, 0, 120),

    -- Rachas (la medalla de cada hito la elige la app según los días)
    ('550e8400-e29b-41d4-a716-446655440008', 'STREAK_3', 'Tres días de racha', 'Llegá a 3 días de racha.', NULL, 'STREAK_DAYS', 3, true, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440003', 'STREAK_WEEK', 'Una semana de racha', 'Llegá a 7 días de racha.', 'https://via.placeholder.com/100?text=Warrior', 'STREAK_DAYS', 7, true, 1, 0),
    ('550e8400-e29b-41d4-a716-446655440009', 'STREAK_14', 'Dos semanas de racha', 'Llegá a 14 días de racha.', NULL, 'STREAK_DAYS', 14, true, 2, 0),
    ('550e8400-e29b-41d4-a716-446655440004', 'STREAK_MONTH', 'Un mes de racha', 'Llegá a 30 días de racha.', 'https://via.placeholder.com/100?text=Legend', 'STREAK_DAYS', 30, true, 3, 0),
    ('550e8400-e29b-41d4-a716-44665544000a', 'STREAK_60', 'Dos meses de racha', 'Llegá a 60 días de racha.', NULL, 'STREAK_DAYS', 60, true, 4, 0),
    ('550e8400-e29b-41d4-a716-44665544000b', 'STREAK_100', 'Cien días de racha', 'Llegá a 100 días de racha.', NULL, 'STREAK_DAYS', 100, true, 5, 0),

    -- Experiencia
    ('550e8400-e29b-41d4-a716-446655440005', 'XP_GRINDER', 'Primeros pasos', 'Obtené 500 XP en total.', 'https://via.placeholder.com/100?text=Grinder', 'TOTAL_XP', 500, true, 0, 10),
    ('550e8400-e29b-41d4-a716-44665544000c', 'XP_5000', 'Aprendiz', 'Obtené 5.000 XP en total.', NULL, 'TOTAL_XP', 5000, true, 0, 30),
    ('550e8400-e29b-41d4-a716-44665544000d', 'XP_25000', 'Experto', 'Obtené 25.000 XP en total.', NULL, 'TOTAL_XP', 25000, true, 0, 100),
    ('550e8400-e29b-41d4-a716-44665544000e', 'XP_100000', 'Maestro', 'Obtené 100.000 XP en total.', NULL, 'TOTAL_XP', 100000, true, 0, 300),
    ('550e8400-e29b-41d4-a716-44665544000f', 'WEEKLY_XP_1000', 'Semana productiva', 'Obtené 1.000 XP en una semana.', NULL, 'WEEKLY_XP', 1000, true, 0, 20),
    ('550e8400-e29b-41d4-a716-446655440010', 'WEEKLY_XP_3000', 'Semana intensa', 'Obtené 3.000 XP en una semana.', NULL, 'WEEKLY_XP', 3000, true, 0, 50),

    -- Social y tienda
    ('550e8400-e29b-41d4-a716-446655440018', 'FIRST_FRIEND', 'Primer amigo', 'Hacé tu primer amigo.', NULL, 'FRIENDS_COUNT', 1, true, 0, 20),
    ('550e8400-e29b-41d4-a716-446655440015', 'FIRST_GIFT', 'Primer regalo', 'Enviá un regalo a otra persona.', NULL, 'GIFTS_SENT', 1, true, 0, 10),
    ('550e8400-e29b-41d4-a716-446655440007', 'GIFT_GIVER', 'Usuario solidario', 'Enviá 5 regalos a otras personas.', 'https://via.placeholder.com/100?text=Giver', 'GIFTS_SENT', 5, true, 0, 30),
    ('550e8400-e29b-41d4-a716-446655440016', 'FIRST_PURCHASE', 'Primera compra', 'Hacé una compra en la tienda.', NULL, 'SHOP_PURCHASES', 1, true, 0, 10),
    ('550e8400-e29b-41d4-a716-446655440017', 'SHOP_5', 'Cliente frecuente', 'Hacé 5 compras en la tienda.', NULL, 'SHOP_PURCHASES', 5, true, 0, 30),

    -- Todavía no hay sistema de desafíos que lo otorgue: queda inactivo (ver UPDATE de abajo).
    ('550e8400-e29b-41d4-a716-446655440006', 'CHALLENGE_CHAMPION', 'Campeón de desafíos', 'Completá 10 desafíos.', 'https://via.placeholder.com/100?text=Champion', 'CHALLENGES_COMPLETED', 10, false, 0, 0)
ON CONFLICT (code) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    criteria_type = EXCLUDED.criteria_type,
    criteria_value = EXCLUDED.criteria_value,
    reward_streak_shields = EXCLUDED.reward_streak_shields,
    reward_gems = EXCLUDED.reward_gems;

-- Rows seeded by an earlier version were created active.
UPDATE achievements SET active = false WHERE code = 'CHALLENGE_CHAMPION';
