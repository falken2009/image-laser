// Image Laser — processus principal Electron
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

function isPro() {
  if (!license || license.status !== "active") return false;
  // Hors ligne : l'abonnement reste valable quelques jours après la dernière vérification
  return Date.now() - (license.checkedAt || 0) < CONFIG.offlineGraceDays * DAY;
}

async function activate(rawKey) {
  const key = String(rawKey || "").trim();
  if (!key) return { ok: false, message: "Saisis ta clé de licence." };
  let r;
  try { r = await lsCall("activate", { license_key: key, instance_name: `Image Laser – ${os.hostname()}` }); }
  catch { return { ok: false, message: "Pas de connexion Internet. Vérifie ta connexion puis réessaie." }; }
  const j = r.json;
  if (!j.activated) {
    const msg = j.error || "";
    if (/limit/i.test(msg)) return { ok: false, message: "Cette clé est déjà utilisée sur le nombre maximum d'ordinateurs. Désactive-la sur un autre PC ou contacte le support." };
    return { ok: false, message: "Clé invalide. Copie la clé reçue par e-mail après ton abonnement." };
  }
  if (!belongsToUs(j.meta)) {
    lsCall("deactivate", { license_key: key, instance_id: j.instance?.id }).catch(() => {});
    return { ok: false, message: "Cette clé ne correspond pas à Image Laser." };
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
  if (!license) return;
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
    used: u.count,
    limit: CONFIG.freeExportsPerDay,
    remaining: Math.max(0, CONFIG.freeExportsPerDay - u.count),
    priceLabel: CONFIG.priceLabel,
  };
}

// ---------- Fenêtre
function createWindow() {
  const win = new BrowserWindow({
    width: 1280, height: 860, minWidth: 420, minHeight: 600,
    backgroundColor: "#0a1838",
    title: "Image Laser",
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
ipcMain.handle("file:show", (_e, p) => shell.showItemInFolder(p));

app.whenReady().then(async () => {
  Menu.setApplicationMenu(null);
  createWindow();
  revalidate();
  setInterval(revalidate, 6 * 60 * 60 * 1000);
  app.on("activate", () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on("window-all-closed", () => { if (process.platform !== "darwin") app.quit(); });
