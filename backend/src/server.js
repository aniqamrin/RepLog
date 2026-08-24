import express from "express";
import cors from "cors";
import { config } from "./config.js";
import { router } from "./routes.js";

const app = express();
app.disable("x-powered-by");
app.use(cors());
app.use(express.json({ limit: "20mb" }));

app.use((err, _req, res, next) => {
  if (err?.type === "entity.too.large") {
    return res.status(413).json({ error: "Request body too large" });
  }
  if (err?.type === "entity.parse.failed") {
    return res.status(400).json({ error: "Invalid JSON body" });
  }
  next(err);
});

app.use("/api", router);

app.use((_req, res) => res.status(404).json({ error: "Not found" }));

app.use((err, _req, res, _next) => {
  const status = err?.status && Number.isInteger(err.status) ? err.status : 500;
  if (status >= 500) console.error(`[error] ${err?.stack || err}`);
  res.status(status).json({ error: status >= 500 ? "Internal server error" : String(err?.message || "Error") });
});

const server = app.listen(config.port, () => {
  console.log(`REPLOG backend listening on http://0.0.0.0:${config.port}`);
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(1), 5000).unref();
  });
}
