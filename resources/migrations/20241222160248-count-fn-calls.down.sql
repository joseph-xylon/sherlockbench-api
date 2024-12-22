ALTER TABLE attempts ADD COLUMN api_calls INTEGER;

--;;

ALTER TABLE attempts ALTER COLUMN fn_calls DROP DEFAULT;

--;;

ALTER TABLE attempts RENAME COLUMN fn_calls TO tool_calls;
