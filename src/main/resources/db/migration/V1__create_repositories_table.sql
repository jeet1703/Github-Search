CREATE TABLE repositories (
    id           BIGINT PRIMARY KEY,
    name         TEXT        NOT NULL,
    full_name    TEXT,
    description  TEXT,
    owner        TEXT,
    language     TEXT,
    stars        INTEGER     NOT NULL,
    forks        INTEGER     NOT NULL,
    open_issues  INTEGER     NOT NULL,
    url          TEXT,
    last_updated TIMESTAMPTZ
);

CREATE INDEX idx_repositories_language ON repositories (lower(language));
CREATE INDEX idx_repositories_stars ON repositories (stars DESC);
