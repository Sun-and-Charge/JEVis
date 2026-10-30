-- Recycle bin: exact deletion time, the user who deleted an object, and repair of objects that earlier versions left
-- outside the bin or without a parent.
-- Relationship types: 1 = parent (startobject is the child), 10 = parent before the object was moved to the bin.

-- Deletion time with seconds (was only the day) and the user object that deleted the object.
ALTER TABLE `object` MODIFY `deletets` DATETIME NULL DEFAULT NULL;
ALTER TABLE `object` ADD COLUMN `deletedby` BIGINT(20) NULL DEFAULT NULL;

-- Deleted objects still linked to a live parent with type 1 (e.g. migration V11) never showed in the bin.
-- Turn the link into type 10 so they appear there; a duplicate type 10 row already exists if the update is ignored.
UPDATE IGNORE `relationship` r
    JOIN `object` c ON c.id = r.startobject
    JOIN `object` p ON p.id = r.endobject
SET r.relationtype = 10
WHERE r.relationtype = 1
  AND c.deletets IS NOT NULL
  AND p.deletets IS NULL;

DELETE r
FROM `relationship` r
         JOIN `object` c ON c.id = r.startobject
         JOIN `object` p ON p.id = r.endobject
WHERE r.relationtype = 1
  AND c.deletets IS NOT NULL
  AND p.deletets IS NULL;

-- Restored objects that kept only their type 10 link had no parent and were invisible: link them again.
UPDATE IGNORE `relationship` r
    JOIN `object` c ON c.id = r.startobject
    JOIN `object` p ON p.id = r.endobject
SET r.relationtype = 1
WHERE r.relationtype = 10
  AND c.deletets IS NULL
  AND p.deletets IS NULL
  AND c.id NOT IN (SELECT startobject FROM (SELECT startobject FROM `relationship` WHERE relationtype = 1) has_parent);

-- Live objects that only hang below deleted objects were invisible everywhere: move them into the bin together with
-- their parent (same deletion time), including their live descendants.
CREATE TEMPORARY TABLE `orphaned_objects` AS
WITH RECURSIVE tree (id, deletets) AS (
    SELECT c.id, MAX(p.deletets)
    FROM `object` c
             JOIN `relationship` r ON r.startobject = c.id AND r.relationtype IN (1, 10)
             JOIN `object` p ON p.id = r.endobject
    WHERE c.deletets IS NULL
      AND c.id NOT IN (SELECT r2.startobject
                       FROM `relationship` r2
                                JOIN `object` p2 ON p2.id = r2.endobject
                       WHERE r2.relationtype = 1
                         AND p2.deletets IS NULL)
    GROUP BY c.id
    HAVING MAX(p.deletets) IS NOT NULL
    UNION
    SELECT r.startobject, t.deletets
    FROM `relationship` r
             JOIN tree t ON r.endobject = t.id
             JOIN `object` c ON c.id = r.startobject
    WHERE r.relationtype = 1
      AND c.deletets IS NULL
)
SELECT id, MAX(deletets) AS deletets
FROM tree
GROUP BY id;

UPDATE `object` o
    JOIN `orphaned_objects` t ON t.id = o.id
SET o.deletets = t.deletets
WHERE o.deletets IS NULL;

DROP TEMPORARY TABLE `orphaned_objects`;
