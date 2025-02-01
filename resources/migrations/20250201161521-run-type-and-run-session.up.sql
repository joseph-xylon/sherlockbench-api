CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

--;;

CREATE TYPE run_state_type AS ENUM ('pending', 'started', 'complete');

--;;

CREATE TYPE run_type_type AS ENUM ('anonymous', 'official');

--;;

ALTER TABLE runs
  ADD COLUMN run_state run_state_type,
  ADD COLUMN run_type run_type_type,
  ADD COLUMN session_id UUID DEFAULT uuid_generate_v4(),
  ADD COLUMN client_id text;
