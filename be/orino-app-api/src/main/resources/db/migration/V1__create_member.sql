CREATE TABLE IF NOT EXISTS member (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    login_id   VARCHAR(50)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_login_id (login_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
