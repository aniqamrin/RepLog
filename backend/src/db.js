import Database from "better-sqlite3";
import fs from "node:fs";
import path from "node:path";
import { config } from "./config.js";

fs.mkdirSync(path.dirname(config.dbPath), { recursive: true });

export const db = new Database(config.dbPath);
db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");

db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL DEFAULT 'Athlete',
  password_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS food_entries (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_id INTEGER NOT NULL,
  date TEXT NOT NULL,
  name TEXT NOT NULL,
  meal_type TEXT NOT NULL DEFAULT 'meal',
  calories INTEGER NOT NULL DEFAULT 0,
  protein_g REAL NOT NULL DEFAULT 0,
  carbs_g REAL NOT NULL DEFAULT 0,
  fat_g REAL NOT NULL DEFAULT 0,
  fiber_g REAL NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (user_id, client_id)
);

CREATE TABLE IF NOT EXISTS workouts (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_id INTEGER NOT NULL,
  date TEXT NOT NULL,
  name TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'strength',
  duration_min INTEGER NOT NULL DEFAULT 0,
  notes TEXT,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (user_id, client_id)
);

CREATE TABLE IF NOT EXISTS weight_entries (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date TEXT NOT NULL,
  weight_kg REAL NOT NULL,
  body_fat_pct REAL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (user_id, date)
);
`);
