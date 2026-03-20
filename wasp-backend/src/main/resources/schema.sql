CREATE TABLE IF NOT EXISTS cpu (
    timestamp_ms INTEGER PRIMARY KEY NOT NULL,
    cpu_ghz REAL NOT NULL,
    cpu_usage REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS cpu_cores (
    timestamp_ms INTEGER NOT NULL,
    core_index INTEGER NOT NULL,
    core_ghz REAL NOT NULL,
    core_usage REAL NOT NULL,
    PRIMARY KEY (timestamp_ms, core_index)
);

CREATE TABLE IF NOT EXISTS memory (
    timestamp_ms INTEGER PRIMARY KEY NOT NULL,
    total_mem INTEGER NOT NULL,
    free_mem INTEGER NOT NULL,
    used_mem INTEGER NOT NULL,
    mem_usage REAL NOT NULL,
    page_faults INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS disk (
    timestamp_ms INTEGER NOT NULL,
    drive_letter TEXT NOT NULL,
    total_space INTEGER NOT NULL,
    free_space INTEGER NOT NULL,
    read_speed REAL,
    write_speed REAL,
    PRIMARY KEY (timestamp_ms, drive_letter)
);

CREATE TABLE IF NOT EXISTS processes (
    timestamp_ms INTEGER NOT NULL,
    pid INTEGER NOT NULL,
    name TEXT NOT NULL,
    owner TEXT,
    priority INTEGER,
    cpu_percent REAL NOT NULL,
    cpu_time INTEGER NOT NULL,
    mem_percent REAL NOT NULL,
    location TEXT,
    PRIMARY KEY (timestamp_ms, PID)
);