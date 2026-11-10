// Pont sécurisé entre l'interface et le processus principal
const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("laser", {
  status: () => ipcRenderer.invoke("licence:status"),
  refresh: () => ipcRenderer.invoke("licence:refresh"),
  activate: key => ipcRenderer.invoke("licence:activate", key),
  deactivate: () => ipcRenderer.invoke("licence:deactivate"),
  openCheckout: () => ipcRenderer.invoke("open:checkout"),
  openPortal: () => ipcRenderer.invoke("open:portal"),
  saveFile: (filename, bytes) => ipcRenderer.invoke("file:save", { filename, bytes }),
  showFile: p => ipcRenderer.invoke("file:show", p),
  sendList: () => ipcRenderer.invoke("send:list"),
  sendTo: (software, filename, bytes) => ipcRenderer.invoke("send:open", { software, filename, bytes }),
  ai: (prompt, imageBase64) => ipcRenderer.invoke("ai:ask", { prompt, imageBase64 }),
});
