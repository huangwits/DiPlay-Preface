CREATE TABLE challenges (
  id TEXT PRIMARY KEY, nonce TEXT NOT NULL, expires INTEGER NOT NULL
);
CREATE INDEX challenges_expiry ON challenges(expires);
CREATE TABLE devices (
  device TEXT PRIMARY KEY,
  request_id TEXT NOT NULL UNIQUE,
  license_id TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL CHECK(status IN ('pending','approved','revoked')),
  valid_until INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  last_seen INTEGER NOT NULL
);
CREATE INDEX devices_listing ON devices(created_at DESC, device DESC);
CREATE TABLE events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at INTEGER NOT NULL, request_id TEXT NOT NULL, action TEXT NOT NULL,
  valid_until INTEGER NOT NULL
);
CREATE TABLE rates (
  id TEXT PRIMARY KEY, count INTEGER NOT NULL, expires INTEGER NOT NULL
);
CREATE INDEX rates_expiry ON rates(expires);
