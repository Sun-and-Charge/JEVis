-- Remove the redundant unique index of the sample table (OpenJEVis #2342).
-- `sampleindex` (object, attribute, timestamp) is identical to the primary key, so every insert maintained the same
-- index twice and it doubled the index storage. Uniqueness stays guaranteed by the primary key.
-- Dropping a secondary index is an in-place metadata change in InnoDB (no table copy).

ALTER TABLE `sample` DROP INDEX IF EXISTS `sampleindex`, ALGORITHM = INPLACE, LOCK = NONE;
