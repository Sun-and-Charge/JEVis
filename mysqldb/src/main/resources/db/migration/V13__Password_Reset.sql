-- Self-service password reset ("forgot password", OpenJEVis #2267).
-- A reset code is sent by mail; only its argon2id hash is stored. A code expires after 30 minutes, allows a few
-- attempts and can be used once. Older codes of a user are invalidated when a new one is requested; the rows are
-- also used to limit the number of requests per user.

CREATE TABLE IF NOT EXISTS `password_reset`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT,
    `user_id`     bigint(20)   NOT NULL,
    `code_hash`   varchar(255) NOT NULL,
    `created`     datetime     NOT NULL,
    `expires`     datetime     NOT NULL,
    `attempts`    int(11)      NOT NULL DEFAULT '0',
    `used`        tinyint(1)   NOT NULL DEFAULT '0',
    `client_info` varchar(255)          DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_password_reset_user` (`user_id`, `created`),
    CONSTRAINT `fk_password_reset_object` FOREIGN KEY (`user_id`) REFERENCES `object` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;
