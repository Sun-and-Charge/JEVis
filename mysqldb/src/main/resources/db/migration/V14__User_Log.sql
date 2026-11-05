-- User activity log in its own table (OpenJEVis #2341).
-- Until now every user action was written as a sample of the attribute "Activities" of the user object. This
-- updated the sample and attribute tables (min/max timestamp) in the request and could lock them; entries within
-- the same second were lost because of the sample primary key. The webservice now writes the log asynchronously
-- into user_log. There is no foreign key to the object table on purpose: an insert would lock the user row, and
-- the log of a deleted user stays readable until the retention time (webservice.userlog.retentiondays) is over.

CREATE TABLE IF NOT EXISTS `user_log`
(
    `id`        bigint(20)   NOT NULL AUTO_INCREMENT,
    `user_id`   bigint(20)   NOT NULL,
    `user_name` varchar(255)          DEFAULT NULL,
    `ts`        datetime(3)  NOT NULL,
    `action`    varchar(64)  NOT NULL,
    `object_id` bigint(20)            DEFAULT NULL,
    `details`   text                  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_user_log_user_ts` (`user_id`, `ts`),
    KEY `idx_user_log_ts` (`ts`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

-- Move the existing "Activities" samples of all users into the new table.
-- Webservice format: "<account>|<EVENT>|<details>", details often start with "<object id>:".
-- Control Center format: "Login: ..." / "Logout: ...". Everything else is kept as action LEGACY.
INSERT INTO `user_log` (`user_id`, `user_name`, `ts`, `action`, `object_id`, `details`)
SELECT p.user_id,
       p.user_name,
       p.ts,
       p.action,
       CASE
           WHEN p.piped = 1 AND p.details REGEXP '^[0-9]{1,18}:'
               THEN CAST(SUBSTRING_INDEX(p.details, ':', 1) AS UNSIGNED) END,
       p.details
FROM (SELECT t.object                                                                      AS user_id,
             t.timestamp                                                                   AS ts,
             t.piped,
             CASE WHEN t.piped = 1 THEN SUBSTRING_INDEX(t.value, '|', 1) ELSE t.name END   AS user_name,
             CASE
                 WHEN t.piped = 1 THEN t.event
                 WHEN t.value LIKE 'Login:%' THEN 'USER_LOGIN'
                 WHEN t.value LIKE 'Logout:%' THEN 'USER_LOGOUT'
                 ELSE 'LEGACY' END                                                         AS action,
             CASE
                 WHEN t.piped = 1 THEN SUBSTRING(t.value, CHAR_LENGTH(SUBSTRING_INDEX(t.value, '|', 2)) + 2)
                 ELSE t.value END                                                          AS details
      FROM (SELECT s.object,
                   s.timestamp,
                   s.value,
                   o.name,
                   SUBSTRING_INDEX(SUBSTRING_INDEX(s.value, '|', 2), '|', -1) AS event,
                   CASE
                       WHEN s.value LIKE '%|%|%' AND SUBSTRING_INDEX(SUBSTRING_INDEX(s.value, '|', 2), '|', -1) IN
                                                     ('USER_LOGIN', 'USER_LOGOUT', 'DELETE_OBJECT',
                                                      'MARK_AS_DELETE_OBJECT', 'DELETE_SAMPLE',
                                                      'DELETE_RELATIONSHIP', 'CREATE_OBJECT', 'CREATE_SAMPLE',
                                                      'CREATE_RELATIONSHIP', 'UPDATE_OBJECT', 'UPDATE_ATTRIBUTE')
                           THEN 1
                       ELSE 0 END                                              AS piped
            FROM `sample` s
                     JOIN `object` o ON o.id = s.object AND o.type = 'User'
            WHERE s.attribute = 'Activities'
              AND s.value IS NOT NULL) t) p;

DELETE s
FROM `sample` s
         JOIN `object` o ON o.id = s.object AND o.type = 'User'
WHERE s.attribute = 'Activities';

UPDATE `attribute` a
    JOIN `object` o ON o.id = a.object AND o.type = 'User'
SET a.mints       = NULL,
    a.maxts       = NULL,
    a.samplecount = 0
WHERE a.name = 'Activities';
