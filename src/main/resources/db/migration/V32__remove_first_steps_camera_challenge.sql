DELETE FROM challenges WHERE code = 'first_steps_camera';

UPDATE challenges SET display_order = display_order - 1
WHERE challenge_type = 'FIRST_STEPS' AND display_order > 2;
