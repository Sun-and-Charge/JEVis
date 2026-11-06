-- Remove the redundant unique index of the sample table (OpenJEVis #2342).
-- `sampleindex` (object, attribute, timestamp) is identical to the primary key, so every insert maintained the same
-- index twice and it doubled the index storage. Uniqueness stays guaranteed by the primary key.
-- Dropping a secondary index is an in-place metadata change in InnoDB (no table copy).
-- MySQL has no DROP INDEX IF EXISTS (MariaDB only), so the index is looked up first; works on both.

SET @drop_sampleindex = IF(EXISTS(SELECT 1
                                  FROM information_schema.statistics
                                  WHERE table_schema = DATABASE()
                                    AND table_name = 'sample'
                                    AND index_name = 'sampleindex'),
                           'ALTER TABLE `sample` DROP INDEX `sampleindex`, ALGORITHM = INPLACE, LOCK = NONE',
                           'DO 0');
PREPARE drop_sampleindex_stmt FROM @drop_sampleindex;
EXECUTE drop_sampleindex_stmt;
DEALLOCATE PREPARE drop_sampleindex_stmt;
