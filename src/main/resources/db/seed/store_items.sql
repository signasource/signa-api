-- Store Items Seed
INSERT INTO shop_items (id, code, title, description, item_type, price_gems, quantity, duration_minutes, multiplier_value, active)
VALUES
    ('550e8400-e29b-41d4-a716-446655440101', 'SHIELD_1', 'Streak Shield', 'Protect your streak for one day if you miss a lesson.', 'STREAK_SHIELD', 50, 1, NULL, NULL, true),
    ('550e8400-e29b-41d4-a716-446655440102', 'SHIELD_3', 'Mega Streak Shield', 'Protect your streak for three days if you miss lessons.', 'STREAK_SHIELD', 120, 3, NULL, NULL, true),
    ('550e8400-e29b-41d4-a716-446655440103', 'LIFE_1', 'Extra Life', 'Get an extra life to continue practicing challenges.', 'LIFE', 30, 1, NULL, NULL, true),
    ('550e8400-e29b-41d4-a716-446655440104', 'LIFE_5', 'Life Bundle', 'Get 5 extra lives for your challenge attempts.', 'LIFE', 120, 5, NULL, NULL, true),
    ('550e8400-e29b-41d4-a716-446655440105', 'XP_MULT_2X_30', '2x XP Boost (30 min)', 'Earn double XP for 30 minutes.', 'XP_MULTIPLIER', 80, 1, 30, 2.0, true),
    ('550e8400-e29b-41d4-a716-446655440106', 'XP_MULT_2X_60', '2x XP Boost (1 hour)', 'Earn double XP for 1 hour.', 'XP_MULTIPLIER', 140, 1, 60, 2.0, true),
    ('550e8400-e29b-41d4-a716-446655440107', 'GEMS_500', 'Gem Pack', 'Get 500 gems to spend in the shop.', 'GEMS', 1000, 500, NULL, NULL, true),
    ('550e8400-e29b-41d4-a716-446655440108', 'GEMS_1500', 'Mega Gem Pack', 'Get 1500 gems to spend in the shop.', 'GEMS', 2500, 1500, NULL, NULL, true)
ON CONFLICT (code) DO NOTHING;
