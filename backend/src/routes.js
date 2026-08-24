import { Router } from "express";
import crypto from "node:crypto";
import { aiConfigured } from "./config.js";
import { chat, vision } from "./ai.js";
import {
  findUserByEmail,
  hashPassword,
  issueToken,
  publicUser,
  requireAuth,
  verifyPassword
} from "./auth.js";
import { db } from "./db.js";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function bad(res, status, error) {
  return res.status(status).json({ error });
}

export const router = Router();

router.get("/health", (_req, res) => {
  res.json({ status: "ok", ai_configured: aiConfigured() });
});

router.post("/auth/register", (req, res) => {
  const email = String(req.body?.email || "").trim().toLowerCase();
  const password = String(req.body?.password || "");
  const name = String(req.body?.name || "").trim() || "Athlete";
  if (!EMAIL_RE.test(email)) return bad(res, 400, "Valid email required");
  if (password.length < 8) return bad(res, 400, "Password must be at least 8 characters");
  if (findUserByEmail(email)) return bad(res, 409, "An account with this email already exists");
  const info = db
    .prepare("INSERT INTO users (email, name, password_hash) VALUES (?, ?, ?)")
    .run(email, name.slice(0, 80), hashPassword(password));
  const user = db.prepare("SELECT * FROM users WHERE id = ?").get(info.lastInsertRowid);
  res.status(201).json({ token: issueToken(user.id), refresh_token: null, user: publicUser(user) });
});

router.post("/auth/login", (req, res) => {
  const email = String(req.body?.email || "").trim().toLowerCase();
  const password = String(req.body?.password || "");
  const user = findUserByEmail(email);
  if (!user || !verifyPassword(password, user.password_hash)) {
    return bad(res, 401, "Invalid email or password");
  }
  res.json({ token: issueToken(user.id), refresh_token: null, user: publicUser(user) });
});

const numOr = (v, fallback) => {
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
};

const cleanFood = (e) => ({
  client_id: Math.trunc(numOr(e?.client_id, 0)),
  date: String(e?.date || "").slice(0, 10),
  name: String(e?.name || "Food").slice(0, 200),
  meal_type: String(e?.meal_type || "meal").slice(0, 40),
  calories: Math.round(numOr(e?.calories, 0)),
  protein_g: numOr(e?.protein_g, 0),
  carbs_g: numOr(e?.carbs_g, 0),
  fat_g: numOr(e?.fat_g, 0),
  fiber_g: numOr(e?.fiber_g, 0)
});

const cleanWorkout = (w) => ({
  client_id: Math.trunc(numOr(w?.client_id, 0)),
  date: String(w?.date || "").slice(0, 10),
  name: String(w?.name || "Workout").slice(0, 200),
  type: String(w?.type || "strength").slice(0, 40),
  duration_min: Math.trunc(numOr(w?.duration_min, 0)),
  notes: w?.notes == null ? null : String(w.notes).slice(0, 2000),
  volume_kg: numOr(w?.volume_kg, 0)
});

const cleanWeight = (w) => ({
  date: String(w?.date || "").slice(0, 10),
  weight_kg: numOr(w?.weight_kg, 0),
  body_fat_pct: w?.body_fat_pct == null ? null : numOr(w.body_fat_pct, null)
});

router.post("/sync", requireAuth, (req, res) => {
  const body = req.body || {};
  const foods = (Array.isArray(body.food_entries) ? body.food_entries : []).map(cleanFood);
  const workouts = (Array.isArray(body.workouts) ? body.workouts : []).map(cleanWorkout);
  const weights = (Array.isArray(body.weight_entries) ? body.weight_entries : []).map(cleanWeight);

  const insertFood = db.prepare(`
    INSERT INTO food_entries (user_id, client_id, date, name, meal_type, calories, protein_g, carbs_g, fat_g, fiber_g)
    VALUES (@user_id, @client_id, @date, @name, @meal_type, @calories, @protein_g, @carbs_g, @fat_g, @fiber_g)
    ON CONFLICT (user_id, client_id) DO UPDATE SET
      date=excluded.date, name=excluded.name, meal_type=excluded.meal_type,
      calories=excluded.calories, protein_g=excluded.protein_g, carbs_g=excluded.carbs_g,
      fat_g=excluded.fat_g, fiber_g=excluded.fiber_g, updated_at=datetime('now')
  `);
  const insertWorkout = db.prepare(`
    INSERT INTO workouts (user_id, client_id, date, name, type, duration_min, notes, volume_kg)
    VALUES (@user_id, @client_id, @date, @name, @type, @duration_min, @notes, @volume_kg)
    ON CONFLICT (user_id, client_id) DO UPDATE SET
      date=excluded.date, name=excluded.name, type=excluded.type,
      duration_min=excluded.duration_min, notes=excluded.notes, volume_kg=excluded.volume_kg,
      updated_at=datetime('now')
  `);
  const insertWeight = db.prepare(`
    INSERT INTO weight_entries (user_id, date, weight_kg, body_fat_pct)
    VALUES (@user_id, @date, @weight_kg, @body_fat_pct)
    ON CONFLICT (user_id, date) DO UPDATE SET
      weight_kg=excluded.weight_kg, body_fat_pct=excluded.body_fat_pct, updated_at=datetime('now')
  `);

  let accepted = 0;
  db.transaction(() => {
    for (const f of foods) {
      if (!f.client_id || !/^\d{4}-\d{2}-\d{2}$/.test(f.date)) continue;
      insertFood.run({ ...f, user_id: req.userId });
      accepted++;
    }
    for (const w of workouts) {
      if (!w.client_id || !/^\d{4}-\d{2}-\d{2}$/.test(w.date)) continue;
      insertWorkout.run({ ...w, user_id: req.userId });
      accepted++;
    }
    for (const w of weights) {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(w.date)) continue;
      insertWeight.run({ ...w, user_id: req.userId });
      accepted++;
    }
  })();

  res.json({ accepted, server_time: new Date().toISOString() });
});

router.get("/friends", requireAuth, (req, res) => {
  const today = String(req.query.date || "").slice(0, 10);
  const weekStart = String(req.query.week_start || "").slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(today)) return bad(res, 400, "date=YYYY-MM-DD required");
  const ws = /^\d{4}-\d{2}-\d{2}$/.test(weekStart) ? weekStart : today;

  const statsStmt = db.prepare(`
    SELECT
      COALESCE(SUM(CASE WHEN date = ? THEN volume_kg ELSE 0 END), 0) AS volume_today,
      COALESCE(SUM(CASE WHEN date BETWEEN ? AND ? THEN volume_kg ELSE 0 END), 0) AS week_volume,
      MAX(CASE WHEN date = ? THEN 1 ELSE 0 END) AS trained_today
    FROM workouts WHERE user_id = ?
  `);

  const baseSelect =
    "SELECT u.id, u.email, u.name FROM friendships f JOIN users u ON u.id = ";

  const friends = db.prepare(baseSelect + "f.friend_id WHERE f.user_id = ? AND f.status = 'accepted'")
    .all(req.userId)
    .map((r) => {
      const s = statsStmt.get(today, ws, today, today, r.id);
      return {
        ...r,
        trained_today: Boolean(s.trained_today),
        volume_today: s.volume_today || 0,
        week_volume: s.week_volume || 0
      };
    });
  const incoming = db.prepare(baseSelect + "f.user_id WHERE f.friend_id = ? AND f.status = 'pending'")
    .all(req.userId);
  const outgoing = db.prepare(baseSelect + "f.friend_id WHERE f.user_id = ? AND f.status = 'pending'")
    .all(req.userId);

  res.json({ friends, incoming, outgoing });
});

router.post("/friends/request", requireAuth, (req, res) => {
  const email = String(req.body?.email || "").trim().toLowerCase();
  const target = db.prepare("SELECT * FROM users WHERE email = ?").get(email);
  if (!target) return bad(res, 404, "No REPLOG account with that email");
  if (target.id === req.userId) return bad(res, 400, "You cannot add yourself");
  const existing = db
    .prepare("SELECT * FROM friendships WHERE user_id = ? AND friend_id = ?")
    .get(req.userId, target.id);
  if (existing?.status === "accepted") return bad(res, 409, "Already friends");
  if (existing) return bad(res, 409, "Request already sent");
  const reverse = db
    .prepare("SELECT * FROM friendships WHERE user_id = ? AND friend_id = ?")
    .get(target.id, req.userId);
  if (reverse?.status === "pending") {
    db.transaction(() => {
      db.prepare("UPDATE friendships SET status = 'accepted' WHERE id = ?").run(reverse.id);
      db.prepare(
        "INSERT INTO friendships (user_id, friend_id, status) VALUES (?, ?, 'accepted') " +
          "ON CONFLICT (user_id, friend_id) DO UPDATE SET status = 'accepted'"
      ).run(req.userId, target.id);
    })();
    return res.json({ status: "accepted" });
  }
  db.prepare("INSERT INTO friendships (user_id, friend_id) VALUES (?, ?)").run(req.userId, target.id);
  res.status(201).json({ status: "pending" });
});

router.post("/friends/respond", requireAuth, (req, res) => {
  const fromId = Math.trunc(numOr(req.body?.user_id, 0));
  const action = String(req.body?.action || "");
  if (!fromId || !["accept", "decline"].includes(action)) {
    return bad(res, 400, "user_id and action (accept|decline) required");
  }
  const row = db
    .prepare("SELECT * FROM friendships WHERE user_id = ? AND friend_id = ? AND status = 'pending'")
    .get(fromId, req.userId);
  if (!row) return bad(res, 404, "No pending request from that user");
  if (action === "accept") {
    db.transaction(() => {
      db.prepare("UPDATE friendships SET status = 'accepted' WHERE id = ?").run(row.id);
      db.prepare(
        "INSERT INTO friendships (user_id, friend_id, status) VALUES (?, ?, 'accepted') " +
          "ON CONFLICT (user_id, friend_id) DO UPDATE SET status = 'accepted'"
      ).run(req.userId, fromId);
    })();
    return res.json({ status: "accepted" });
  }
  db.prepare("DELETE FROM friendships WHERE id = ?").run(row.id);
  res.json({ status: "declined" });
});

router.post("/friends/remove", requireAuth, (req, res) => {
  const friendId = Math.trunc(numOr(req.body?.user_id, 0));
  if (!friendId) return bad(res, 400, "user_id required");
  db.prepare("DELETE FROM friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)")
    .run(req.userId, friendId, friendId, req.userId);
  res.json({ removed: true });
});

router.get("/friends/leaderboard", requireAuth, (req, res) => {
  const today = String(req.query.date || "").slice(0, 10);
  const weekStart = String(req.query.week_start || "").slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(today)) return bad(res, 400, "date=YYYY-MM-DD required");
  const ws = /^\d{4}-\d{2}-\d{2}$/.test(weekStart) ? weekStart : today;

  const ids = db.prepare(`
    SELECT CASE WHEN user_id = ? THEN friend_id ELSE user_id END AS other_id
    FROM friendships WHERE status = 'accepted' AND (user_id = ? OR friend_id = ?)
  `).all(req.userId, req.userId, req.userId).map((r) => r.other_id);
  ids.push(req.userId);

  const placeholders = ids.map(() => "?").join(",");
  const rows = db.prepare(`
    SELECT u.id, u.name, u.email,
      COALESCE(SUM(CASE WHEN w.date = ? THEN w.volume_kg ELSE 0 END), 0) AS volume_today,
      COALESCE(SUM(CASE WHEN w.date BETWEEN ? AND ? THEN w.volume_kg ELSE 0 END), 0) AS week_volume,
      MAX(CASE WHEN w.date = ? THEN 1 ELSE 0 END) AS trained_today
    FROM users u LEFT JOIN workouts w ON w.user_id = u.id AND w.type = 'strength'
    WHERE u.id IN (${placeholders})
    GROUP BY u.id
  `).all(today, ws, today, today, ...ids);

  const entries = rows.map((r) => ({
    id: r.id,
    name: r.name,
    email: r.email,
    is_me: r.id === req.userId,
    trained_today: Boolean(r.trained_today),
    volume_today: r.volume_today || 0,
    week_volume: r.week_volume || 0
  })).sort((a, b) => b.week_volume - a.week_volume || b.volume_today - a.volume_today);
  res.json({ entries });
});

router.post("/ai/chat", async (req, res, next) => {
  if (!aiConfigured()) return bad(res, 503, "AI is not configured on this server (set OPENAI_API_KEY)");
  try {
    const { reply, model } = await chat(req.body?.messages, req.body?.context);
    res.json({ reply, model });
  } catch (err) {
    next(err);
  }
});

router.post("/ai/vision", async (req, res, next) => {
  if (!aiConfigured()) return bad(res, 503, "AI is not configured on this server (set OPENAI_API_KEY)");
  try {
    const { content, model } = await vision(req.body?.image_base64, req.body?.prompt);
    res.json({ raw: content, model });
  } catch (err) {
    next(err);
  }
});

export function requestTag() {
  return crypto.randomUUID().slice(0, 8);
}
