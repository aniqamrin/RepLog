import { config } from "./config.js";

const SYSTEM_PROMPT =
  "You are REPLOG AI, a concise fitness and nutrition coach. You receive structured JSON context " +
  "with the user's real logged data. Reference only numbers present in that context. Never invent statistics. " +
  "Be direct, practical, and brief.";

async function callOpenAI(messages, model) {
  const res = await fetch(`${config.openaiBaseUrl}/chat/completions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${config.openaiApiKey}`
    },
    body: JSON.stringify({ model, messages }),
    signal: AbortSignal.timeout(90_000)
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => "");
    throw Object.assign(new Error(`AI provider error ${res.status}: ${detail.slice(0, 300)}`), {
      status: 502
    });
  }
  const data = await res.json();
  const content = data?.choices?.[0]?.message?.content;
  if (!content || !content.trim()) throw Object.assign(new Error("Empty AI reply"), { status: 502 });
  return { content: content.trim(), model: data.model || model };
}

export async function chat(messages, context) {
  const mapped = (Array.isArray(messages) ? messages : [])
    .filter((m) => m && typeof m.content === "string" && m.content.trim())
    .slice(-12)
    .map((m) => ({
      role: m.role === "assistant" ? "assistant" : "user",
      content: String(m.content).slice(0, 8000)
    }));
  return callOpenAI(
    [
      { role: "system", content: SYSTEM_PROMPT },
      { role: "system", content: `User data context:\n${String(context || "{}").slice(0, 12000)}` },
      ...mapped
    ],
    config.chatModel
  );
}

export async function vision(imageBase64, prompt) {
  const clean = String(imageBase64 || "").replace(/^data:image\/\w+;base64,/, "");
  if (!clean || clean.length < 100) {
    throw Object.assign(new Error("image_base64 is required"), { status: 400 });
  }
  return callOpenAI(
    [
      {
        role: "user",
        content: [
          { type: "text", text: String(prompt || "Analyze this food photo.") },
          { type: "image_url", image_url: { url: `data:image/jpeg;base64,${clean}`, detail: "low" } }
        ]
      }
    ],
    config.visionModel
  );
}
