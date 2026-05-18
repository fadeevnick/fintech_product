const crypto = require("crypto");
const fs = require("fs");
const http = require("http");

const port = Number(process.env.MINIFIN_WEBHOOK_RECEIVER_PORT || "39091");
const secret = process.env.MINIFIN_WEBHOOK_SECRET;
const receivedFile = process.env.MINIFIN_WEBHOOK_RECEIVED_FILE || "/tmp/minifin-phase06-webhooks.jsonl";
const responseStatus = Number(process.env.MINIFIN_WEBHOOK_RESPONSE_STATUS || "200");

function validSignature(body, timestamp, header) {
  const match = /^t=([^,]+),v1=([0-9a-f]+)$/i.exec(header || "");
  if (!match || match[1] !== timestamp) return false;
  const expected = crypto.createHmac("sha256", secret).update(`${timestamp}.${body}`).digest("hex");
  const actual = Buffer.from(match[2]);
  const expectedBuffer = Buffer.from(expected);
  return actual.length === expectedBuffer.length && crypto.timingSafeEqual(expectedBuffer, actual);
}

const server = http.createServer((req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200).end("ok");
    return;
  }
  if (req.method !== "POST" || req.url !== "/webhooks/minifin") {
    res.writeHead(404).end("not found");
    return;
  }
  const chunks = [];
  req.on("data", (chunk) => chunks.push(chunk));
  req.on("end", () => {
    const body = Buffer.concat(chunks).toString("utf8");
    const timestamp = req.headers["minifin-webhook-timestamp"];
    const signature = req.headers["minifin-webhook-signature"];
    if (!secret || !validSignature(body, timestamp, signature)) {
      res.writeHead(400).end("invalid signature");
      return;
    }
    const record = {
      id: req.headers["minifin-webhook-id"],
      event: req.headers["minifin-webhook-event"],
      attempt: req.headers["minifin-webhook-attempt"],
      body: JSON.parse(body),
    };
    fs.appendFileSync(receivedFile, `${JSON.stringify(record)}\n`);
    res.writeHead(responseStatus, { "content-type": "application/json" }).end(JSON.stringify({ ok: responseStatus >= 200 && responseStatus < 300 }));
  });
});

server.listen(port, "0.0.0.0");
