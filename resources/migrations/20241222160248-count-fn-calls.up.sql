ALTER TABLE attempts RENAME COLUMN tool_calls TO fn_calls;

--;;

ALTER TABLE attempts ALTER COLUMN fn_calls SET DEFAULT 0;

--;;

ALTER TABLE attempts DROP COLUMN api_calls;
