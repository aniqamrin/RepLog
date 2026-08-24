import crypto from "node:crypto";
import { config } from "./config.js";
import { db } from "./db.js";

export function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString("hex");
  const hash = crypto.scryptSync(password, salt, 64).toString("hex");
  return `scrypt$${salt}$${hash}`;
}

export function verifyPassword(password, stored) {
  const [scheme, salt, hash] = String(stored).split("$");
  if (scheme !== "scrypt" || !salt || !hash) return false;
  const candidate = crypto.scryptSync(password, salt, 64);
  const expected = Buffer.from(hash, "hex");
  return candidate.length === expected.length && crypto.timingSafeEqual(candidate, expected);
}

function b64u(buf) {
  return Buffer.from(buf).toString("base64url");
}

function sign(data) {
  return crypto.createHmac("sha256", config.jwtSecret).update(data).digest("base64url");
}

export function issueToken(userId) {
  const header = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64u(
    JSON.stringify({
      sub: String(userId),
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + config.tokenTtlSeconds
    })
  );
  return `${header}.${payload}.${sign(`${header}.${payload}`)}`;
}

export function verifyToken(token) {
  const parts = String(token || "").split(".");
  if (parts.length !== 3) return null;
  const [header, payload, signature] = parts;
  const expected = sign(`${header}.${payload}`);
  const a = Buffer.from(signature);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  try {
    const claims = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
    if (!claims.exp || claims.exp < Math.floor(Date.now() / 1000)) return null;
    const userId = Number(claims.sub);
    return Number.isInteger(userId) && userId > 0 ? userId : null;
  } catch {
    return null;
  }
}

export function requireAuth(req, res, next) {
  const header = req.get("Authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  const userId = verifyToken(token);
  if (!userId) return res.status(401).json({ error: "Invalid or expired token" });
  req.userId = userId;
  next();
}

export function publicUser(row) {
  return { id: String(row.id), email: row.email, name: row.name };
}

export function findUserByEmail(email) {
  return db.prepare("SELECT * FROM users WHERE email = ?").get(String(email).trim().toLowerCase());
}
