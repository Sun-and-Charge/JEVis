-- JEDataCollector ships its data source and parser drivers built in, the driver directories below the service object
-- are no longer read and their classes were removed. Soft-delete the remaining driver objects and everything below
-- them (same as deleting in the Control Center: deletets is set, the rows stay recoverable).
-- Relationship type 1 is the parent relation, startobject is the child and endobject the parent.

CREATE TEMPORARY TABLE `driver_objects` AS
WITH RECURSIVE tree (id) AS (
    SELECT o.id
    FROM object o
    WHERE o.type IN ('Driver Directory',
                     'Data Source Driver Directory', 'Parser Driver Directory',
                     'Converter Driver Directory', 'Importer Driver Directory',
                     'Driver', 'Data Source Driver', 'Parser Driver', 'Converter Driver', 'Importer Driver')
      AND o.deletets IS NULL
    UNION
    SELECT r.startobject
    FROM relationship r
             JOIN tree t ON r.endobject = t.id
    WHERE r.relationtype = 1
)
SELECT id
FROM tree;

UPDATE object
SET deletets = NOW()
WHERE deletets IS NULL
  AND id IN (SELECT id FROM driver_objects);

DROP TEMPORARY TABLE `driver_objects`;
