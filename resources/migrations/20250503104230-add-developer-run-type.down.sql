-- Note: PostgreSQL doesn't support removing enum values directly.
-- To properly revert, we'd need to create a new type, update all references, and drop the old type.
-- This is a complex operation that would be better handled manually or with a more comprehensive migration.
-- For this reason, this down migration is a no-op.

-- If you need to completely remove the 'developer' value, you would:
-- 1. Create a new type without the 'developer' value
-- 2. Convert all columns using this type to use the new type
-- 3. Drop the old type
-- 4. Rename the new type to the original name