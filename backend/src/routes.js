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
  notes: w?.notes == null ? null : String(w.notes).slice(0, 2000)
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
    INSERT INTO workouts (user_id, client_id, date, name, type, duration_min, notes)
    VALUES (@user_id, @client_id, @date, @name, @type, @duration_min, @notes)
    ON CONFLICT (user_id, client_id) DO UPDATE SET
      date=excluded.date, name=excluded.name, type=excluded.type,
      duration_min=excluded.duration_min, notes=excluded.notes, updated_at=datetime('now')
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
