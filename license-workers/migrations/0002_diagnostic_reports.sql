CREATE TABLE diagnostic_reports (
  id TEXT PRIMARY KEY,
  device TEXT NOT NULL,
  digest TEXT NOT NULL,
  version TEXT NOT NULL,
  description TEXT NOT NULL,
  report TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires INTEGER NOT NULL,
  UNIQUE(device, digest)
);
CREATE INDEX diagnostic_reports_expiry ON diagnostic_reports(expires);
CREATE INDEX diagnostic_reports_listing ON diagnostic_reports(created_at DESC, id DESC);
CREATE INDEX diagnostic_reports_device ON diagnostic_reports(device, created_at);
