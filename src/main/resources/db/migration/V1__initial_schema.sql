CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(50),
    enabled BOOLEAN NOT NULL
);

CREATE TABLE user_settings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    timezone VARCHAR(255) NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    theme VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    font_size VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    vibration_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    daily_goal_minutes INT NOT NULL DEFAULT 15,
    daily_notification_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    daily_notification_time TIME NOT NULL DEFAULT '20:00:00'
);

CREATE TABLE tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE sign_languages (
    id UUID PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    country_code VARCHAR(3) NOT NULL
);

CREATE TABLE courses (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    is_free BOOLEAN NOT NULL,
    cover_url VARCHAR(255),
    sign_language_id UUID NOT NULL REFERENCES sign_languages(id)
);

CREATE TABLE course_versions (
    id UUID PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL,
    course_id UUID NOT NULL REFERENCES courses(id)
);

CREATE TABLE topics (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    "order" INT NOT NULL,
    cover_url VARCHAR(255),
    course_version_id UUID NOT NULL REFERENCES course_versions(id)
);

CREATE TABLE lessons (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    "order" INT NOT NULL,
    topic_id UUID NOT NULL REFERENCES topics(id)
);

CREATE TABLE lesson_blocks (
    id UUID PRIMARY KEY,
    type VARCHAR(30) NOT NULL,
    "order" INT NOT NULL,
    config TEXT,
    xp_reward INT NOT NULL,
    is_exam_eligible BOOLEAN NOT NULL,
    lesson_id UUID NOT NULL REFERENCES lessons(id)
);
