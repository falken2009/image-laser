// Laser Pix — processus principal Electron
// Gère la fenêtre, l'enregistrement des fichiers, la licence Lemon Squeezy et le quota gratuit.
const { app, BrowserWindow, ipcMain, dialog, shell, safeStorage, Menu } = require("electron");
const path = require("path");
const fs = require("fs");
const os = require("os");

const CONFIG = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "config.json"), "utf8"));
const API = "https://api.lemonsqueezy.com/v1/licenses";
const DAY = 24 * 60 * 60 * 1000;

// ---------- Stockage local (dossier AppData de l'utilisateur)
const dataFile = name => path.join(app.getPath("userData"), name);
function readJson(name, fallback) {
  try { return JSON.parse(fs.readFileSync(dataFile(name), "utf8")); } catch { return fallback; }
}
function writeJson(name, value) {
  fs.mkdirSync(app.getPath("userData"), { recursive: true });
  fs.writeFileSync(dataFile(name), JSON.stringify(value, null, 2));
}
function encrypt(text) {
  return safeStorage.isEncryptionAvailable() ? safeStorage.encryptString(text).toString("base64") : "plain:" + text;
}
function decrypt(blob) {
  if (!blob) return "";
  if (blob.startsWith("plain:")) return blob.slice(6);
  try { return safeStorage.decryptString(Buffer.from(blob, "base64")); } catch { return ""; }
}

// ---------- Licence
let license = readJson("licence.json", null); // { key, instanceId, status, checkedAt, customer, variant }

async function lsCall(action, params) {
  const body = new URLSearchParams(params);
  const res = await fetch(`${API}/${action}`, {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  const json = await res.json().catch(() => ({}));
  return { ok: res.ok, json };
}

// Vérifie que la clé vient bien de TA boutique et de TON produit
function belongsToUs(meta) {
  if (!meta) return false;
  if (CONFIG.storeId && Number(meta.store_id) !== Number(CONFIG.storeId)) return false;
  if (CONFIG.productId && Number(meta.product_id) !== Number(CONFIG.productId)) return false;
  return true;
}

function saveLicense(l) { license = l; writeJson("licence.json", l); }
function clearLicense() { license = null; try { fs.unlinkSync(dataFile("licence.json")); } catch {} }

// ---------- Licences à vie (signées avec la clé privée du propriétaire, vérifiées hors ligne)
const LIFETIME_PUBLIC_KEY = `-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAF2fqW6U33V9o3QeHGGyHtZ5oifZOEpZVkhCCeF1cQig=
-----END PUBLIC KEY-----`;
const crypto = require("crypto");
const fromB64u = s => Buffer.from(String(s).replace(/-/g, "+").replace(/_/g, "/"), "base64");
function checkLifetime(key) {
  const m = /^LP1-([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)$/.exec(String(key || "").replace(/\s+/g, ""));
  if (!m) return null;
  try {
    const payload = fromB64u(m[1]), sig = fromB64u(m[2]);
    if (!crypto.verify(null, payload, crypto.createPublicKey(LIFETIME_PUBLIC_KEY), sig)) return null;
    const data = JSON.parse(payload.toString("utf8"));
    return data && data.t === "vie" ? data : null;
  } catch { return null; }
}
let lifetimeOk = null;
function isLifetime() {
  if (!license || !license.lifetime) return false;
  if (lifetimeOk === null) lifetimeOk = !!checkLifetime(decrypt(license.key));
  return lifetimeOk;
}

function isPro() {
  if (isLifetime()) return true;
  if (!license || license.status !== "active") return false;
  // Hors ligne : l'abonnement reste valable quelques jours après la dernière vérification
  return Date.now() - (license.checkedAt || 0) < CONFIG.offlineGraceDays * DAY;
}

async function activate(rawKey) {
  const key = String(rawKey || "").trim();
  if (!key) return { ok: false, message: "Saisis ta clé de licence." };
  if (/^LP1-/.test(key.replace(/\s+/g, ""))) {
    const data = checkLifetime(key);
    if (!data) return { ok: false, message: "Cette licence à vie n'est pas valide. Copie-la en entier, sans espace." };
    const clean = key.replace(/\s+/g, "");
    saveLicense({ key: encrypt(clean), lifetime: true, status: "active", checkedAt: Date.now(), customer: data.n || "", variant: "Licence à vie", last4: clean.slice(-4) });
    lifetimeOk = true;
    return { ok: true, message: `Licence à vie activée${data.n ? " pour " + data.n : ""}. Exports illimités, sans abonnement.` };
  }
  let r;
  try { r = await lsCall("activate", { license_key: key, instance_name: `Laser Pix – ${os.hostname()}` }); }
  catch { return { ok: false, message: "Pas de connexion Internet. Vérifie ta connexion puis réessaie." }; }
  const j = r.json;
  if (!j.activated) {
    const msg = j.error || "";
    if (/limit/i.test(msg)) return { ok: false, message: "Cette clé est déjà utilisée sur le nombre maximum d'ordinateurs. Désactive-la sur un autre PC ou contacte le support." };
    return { ok: false, message: "Clé invalide. Copie la clé reçue par e-mail après ton abonnement." };
  }
  if (!belongsToUs(j.meta)) {
    lsCall("deactivate", { license_key: key, instance_id: j.instance?.id }).catch(() => {});
    return { ok: false, message: "Cette clé ne correspond pas à Laser Pix." };
  }
  saveLicense({
    key: encrypt(key),
    instanceId: j.instance.id,
    status: j.license_key.status,
    checkedAt: Date.now(),
    customer: j.meta.customer_name || "",
    variant: j.meta.variant_name || "",
    last4: key.slice(-4),
  });
  return { ok: true, message: "Abonnement activé. Merci !" };
}

async function revalidate() {
  if (!license || license.lifetime) return;
  const key = decrypt(license.key);
  if (!key) { clearLicense(); return; }
  try {
    const r = await lsCall("validate", { license_key: key, instance_id: license.instanceId });
    const j = r.json;
    if (j.valid && belongsToUs(j.meta)) {
      saveLicense({ ...license, status: j.license_key.status, checkedAt: Date.now() });
    } else if (r.ok || j.error) {
      // Abonnement arrêté, expiré ou clé désactivée
      saveLicense({ ...license, status: j.license_key?.status || "inactive", checkedAt: Date.now() });
    }
  } catch {
    // Hors ligne : on garde l'état actuel (délai de grâce)
  }
}

async function deactivate() {
  if (!license) return { ok: true };
  if (license.lifetime) { clearLicense(); lifetimeOk = null; return { ok: true, message: "Licence à vie retirée de cet ordinateur. Tu peux la réactiver à tout moment avec la même clé." }; }
  const key = decrypt(license.key);
  try { await lsCall("deactivate", { license_key: key, instance_id: license.instanceId }); } catch {}
  clearLicense();
  return { ok: true, message: "Licence désactivée sur cet ordinateur." };
}

// ---------- Quota gratuit (exports par jour)
const today = () => new Date().toISOString().slice(0, 10);
function usage() {
  const u = readJson("usage.json", { day: today(), count: 0 });
  if (u.day !== today()) return { day: today(), count: 0 };
  return u;
}
function status() {
  const u = usage();
  return {
    pro: isPro(),
    status: license?.status || "none",
    last4: license?.last4 || "",
    customer: license?.customer || "",
    variant: license?.variant || "",
    lifetime: isLifetime(),
    used: u.count,
    limit: CONFIG.freeExportsPerDay,
    remaining: Math.max(0, CONFIG.freeExportsPerDay - u.count),
    priceLabel: CONFIG.priceLabel,
  };
}

// ---------- Fenêtre
function createWindow() {
  const win = new BrowserWindow({
    width: 1440, height: 900, minWidth: 420, minHeight: 600,
    backgroundColor: "#09090b",
    title: "Laser Pix",
    icon: path.join(__dirname, "..", "build", "icon.png"),
    webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  win.loadFile(path.join(__dirname, "renderer", "index.html"));
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  win.webContents.on("will-navigate", e => e.preventDefault());
}

ipcMain.handle("licence:status", async () => status());
ipcMain.handle("licence:refresh", async () => { await revalidate(); return status(); });
ipcMain.handle("licence:activate", async (_e, key) => ({ ...(await activate(key)), state: status() }));
ipcMain.handle("licence:deactivate", async () => ({ ...(await deactivate()), state: status() }));
ipcMain.handle("open:checkout", () => shell.openExternal(CONFIG.checkoutUrl));
ipcMain.handle("open:portal", () => shell.openExternal(CONFIG.portalUrl));

// Enregistre un fichier exporté. Compte l'export seulement s'il est réellement enregistré.
ipcMain.handle("file:save", async (e, { filename, bytes }) => {
  const st = status();
  if (!st.pro && st.remaining <= 0) return { result: "limit", state: st };
  const ext = path.extname(filename).slice(1).toLowerCase();
  const win = BrowserWindow.fromWebContents(e.sender);
  const { canceled, filePath } = await dialog.showSaveDialog(win, {
    title: "Enregistrer le fichier de gravure",
    defaultPath: path.join(app.getPath("pictures"), filename),
    filters: [ext === "svg" ? { name: "Image vectorielle SVG", extensions: ["svg"] } : { name: "Image PNG", extensions: ["png"] }],
  });
  if (canceled || !filePath) return { result: "canceled", state: status() };
  fs.writeFileSync(filePath, Buffer.from(bytes));
  if (!st.pro) { const u = usage(); u.count += 1; writeJson("usage.json", u); }
  return { result: "saved", path: filePath, state: status() };
});
// ---------- IA en ligne (API Anthropic), réservée aux abonnés Pro
function extractJson(text) {
  const a = text.indexOf("{"), b = text.lastIndexOf("}");
  if (a < 0 || b <= a) return null;
  try { return JSON.parse(text.slice(a, b + 1)); } catch { return null; }
}
ipcMain.handle("ai:ask", async (_e, { prompt, imageBase64 }) => {
  if (!CONFIG.anthropicApiKey) return { ok: false, reason: "nokey" };
  if (!isPro()) return { ok: false, reason: "pro" };
  try {
    const res = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: { "x-api-key": CONFIG.anthropicApiKey, "anthropic-version": "2023-06-01", "content-type": "application/json" },
      body: JSON.stringify({
        model: CONFIG.aiModel || "claude-sonnet-5-5",
        max_tokens: 900,
        messages: [{ role: "user", content: [
          ...(imageBase64 ? [{ type: "image", source: { type: "base64", media_type: "image/jpeg", data: imageBase64 } }] : []),
          { type: "text", text: prompt },
        ] }],
      }),
    });
    const j = await res.json().catch(() => ({}));
    if (!res.ok) return { ok: false, reason: "api", message: j?.error?.message || String(res.status) };
    const text = (j.content || []).filter(c => c.type === "text").map(c => c.text).join("\n");
    const data = extractJson(text);
    return data ? { ok: true, data } : { ok: false, reason: "json" };
  } catch (err) {
    return { ok: false, reason: "offline" };
  }
});
// ---------- Envoi vers le logiciel du graveur
const { spawn } = require("child_process");
const PF = process.env["ProgramFiles"] || "C:\\Program Files";
const PF86 = process.env["ProgramFiles(x86)"] || "C:\\Program Files (x86)";
const LAD = process.env["LOCALAPPDATA"] || path.join(os.homedir(), "AppData", "Local");
const SOFTWARE = {
  xcs: { name: "xTool Creative Space", paths: [
    path.join(PF, "xTool Creative Space", "xTool Creative Space.exe"),
    path.join(LAD, "Programs", "xTool Creative Space", "xTool Creative Space.exe"),
    path.join(LAD, "Programs", "xtool-creative-space", "xTool Creative Space.exe"),
    path.join(PF, "xTool", "xTool Creative Space", "xTool Creative Space.exe") ] },
  lightburn: { name: "LightBurn", paths: [path.join(PF, "LightBurn", "LightBurn.exe"), path.join(PF86, "LightBurn", "LightBurn.exe")] },
  lasergrbl: { name: "LaserGRBL", paths: [path.join(PF86, "LaserGRBL", "LaserGRBL.exe"), path.join(PF, "LaserGRBL", "LaserGRBL.exe")] },
  custom: { name: "Autre logiciel…", paths: [] },
};
function settings() { return readJson("reglages.json", { software: "xcs", custom: {} }); }
function findExe(id) {
  const st = settings();
  if (st.custom?.[id] && fs.existsSync(st.custom[id])) return st.custom[id];
  return (SOFTWARE[id]?.paths || []).find(p => fs.existsSync(p)) || "";
}
ipcMain.handle("send:list", () => {
  const st = settings();
  return Object.entries(SOFTWARE).map(([id, s]) => ({
    id, installed: !!findExe(id), selected: st.software === id,
    name: id === "custom" && st.custom?.custom ? path.basename(st.custom.custom, ".exe") : s.name,
  }));
});
ipcMain.handle("send:open", async (e, { software, filename, bytes }) => {
  const st0 = status();
  if (!st0.pro && st0.remaining <= 0) return { result: "limit", state: st0 };
  const win = BrowserWindow.fromWebContents(e.sender);
  const appName = SOFTWARE[software]?.name || "le logiciel";
  let exe = findExe(software);
  if (!exe) {
    const pick = await dialog.showOpenDialog(win, {
      title: software === "custom" ? "Choisis le logiciel de ton graveur" : `Où se trouve ${appName} ?`,
      defaultPath: PF, properties: ["openFile"], filters: [{ name: "Programme", extensions: ["exe"] }],
    });
    if (pick.canceled || !pick.filePaths[0]) return { result: "notfound", app: appName, state: status() };
    exe = pick.filePaths[0];
    const s = settings(); s.custom = { ...(s.custom || {}), [software]: exe }; writeJson("reglages.json", s);
  }
  const s = settings(); s.software = software; writeJson("reglages.json", s);
  const dir = path.join(app.getPath("pictures"), "Laser Pix");
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, filename);
  fs.writeFileSync(file, Buffer.from(bytes));
  try {
    const child = spawn(exe, [file], { detached: true, stdio: "ignore", cwd: path.dirname(exe) });
    child.on("error", () => {});
    child.unref();
  } catch (err) {
    return { result: "error", message: `Impossible de lancer ${appName}.`, state: status() };
  }
  shell.showItemInFolder(file);
  if (!st0.pro) { const u = usage(); u.count += 1; writeJson("usage.json", u); }
  const shown = software === "custom" ? path.basename(exe, ".exe") : appName;
  return { result: "sent", app: shown, path: file, state: status() };
});
ipcMain.handle("file:show", (_e, p) => shell.showItemInFolder(p));

app.whenReady().then(async () => {
  Menu.setApplicationMenu(null);
  createWindow();
  revalidate();
  setInterval(revalidate, 6 * 60 * 60 * 1000);
  app.on("activate", () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on("window-all-closed", () => { if (process.platform !== "darwin") app.quit(); });
