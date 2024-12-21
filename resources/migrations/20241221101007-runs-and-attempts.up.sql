CREATE TABLE runs (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    benchmark_version VARCHAR NOT NULL, -- SemVer stored as string
    config JSONB NOT NULL,
    datetime_start TIMESTAMP DEFAULT NOW(),
    total_run_time FLOAT, -- Storing seconds as float
    total_api_calls INTEGER,
    final_score JSONB,
    score_percent FLOAT
);

--;;

CREATE TABLE attempts (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES runs(id) ON DELETE CASCADE,
    function_name VARCHAR NOT NULL, -- doubles as the id of the function
    verifications JSONB NOT NULL,
    result_value VARCHAR,
    time_taken FLOAT, -- Storing seconds as float
    tool_calls INTEGER,
    api_calls INTEGER,
    complete_log TEXT
);
