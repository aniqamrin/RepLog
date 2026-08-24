import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function num(value, fallback) {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

export const config = {
  port: num(process.env.PORT, 4000),
  dbPath: process.env.DB_PATH || path.join(__dirname, "..", "data", "replog.db"),
  jwtSecret: process.env.JWT_SECRET || crypto.randomBytes(32).toString("hex"),
  tokenTtlSeconds: num(process.env.TOKEN_TTL_SECONDS, 60 * 60 * 24 * 30),
  openaiBaseUrl: (process.env.OPENAI_BASE_URL || "https://api.openai.com/v1").replace(/\/+$/, ""),
  openaiApiKey: process.env.OPENAI_API_KEY || "",
  chatModel: process.env.CHAT_MODEL || "gpt-4o-mini",
  visionModel: process.env.VISION_MODEL || "gpt-4o-mini"
};

export function aiConfigured() {
  return Boolean(config.openaiApiKey);
}
