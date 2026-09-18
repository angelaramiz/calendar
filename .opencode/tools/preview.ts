import { tool } from "@opencode-ai/plugin"
import path from "path"

const SDK_FALLBACK = "C:\\Users\\angel\\AppData\\Local\\Android\\Sdk"
const AVD_DEFAULT = "Medium_Phone_API_35"
const PACKAGE = "com.fintrack.app"

function sdk(): string {
  return process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || SDK_FALLBACK
}

function adb(): string {
  return path.join(sdk(), "platform-tools", "adb.exe")
}

function emu(): string {
  return path.join(sdk(), "emulator", "emulator.exe")
}

async function run(cmd: string[]): Promise<string> {
  const proc = Bun.spawn(cmd, { stdout: "pipe", stderr: "pipe" })
  const [out, err, code] = await Promise.all([
    new Response(proc.stdout).text(),
    new Response(proc.stderr).text(),
    proc.exited,
  ])
  if (code !== 0) throw new Error(`${cmd.join(" ")}\n${err.trim() || out.trim()}`)
  return out.trim()
}

export default tool({
  description:
    "Controla el emulador Android y FinTrack: status, boot, install, launch, screenshot, tap. " +
    "screenshot guarda un PNG y devuelve su ruta (léelo con read para ver la app).",
  args: {
    action: tool.schema
      .enum(["status", "boot", "install", "launch", "screenshot", "tap"])
      .describe("Qué hacer en el emulador"),
    x: tool.schema.number().optional().describe("tap: coordenada X"),
    y: tool.schema.number().optional().describe("tap: coordenada Y"),
    out: tool.schema
      .string()
      .optional()
      .describe("screenshot: ruta del PNG (defecto .opencode/preview.png)"),
  },
  async execute(args, context) {
    const A = adb()
    switch (args.action) {
      case "status":
        return await run([A, "devices", "-l"])

      case "boot": {
        const already = await run([A, "devices"]).catch(() => "")
        if (/emulator-\d+\s+device/.test(already)) return "Emulador ya conectado."
        const proc = Bun.spawn([emu(), "-avd", AVD_DEFAULT], {
          stdout: "ignore",
          stderr: "ignore",
          stdin: "ignore",
        })
        proc.unref()
        for (let i = 0; i < 60; i++) {
          await Bun.sleep(3000)
          const done = await run([A, "shell", "getprop", "sys.boot_completed"]).catch(
            () => "",
          )
          if (done.trim() === "1") return "Emulador listo."
        }
        return "El emulador sigue arrancando; reintenta status en unos segundos."
      }

      case "install": {
        const apk = path.join(context.worktree, "calendarWeb", "calendarfinance.apk")
        return await run([A, "install", "-r", apk])
      }

      case "launch":
        return await run([
          A,
          "shell",
          "monkey",
          "-p",
          PACKAGE,
          "-c",
          "android.intent.category.LAUNCHER",
          "1",
        ])

      case "screenshot": {
        // Guardado en el dispositivo + pull: evita corromper binarios
        // por el stdout del shell en Windows.
        const outPath = args.out || path.join(context.worktree, ".opencode", "preview.png")
        await run([A, "shell", "screencap", "-p", "/sdcard/fintrack_preview.png"])
        await run([A, "pull", "/sdcard/fintrack_preview.png", outPath])
        return outPath
      }

      case "tap": {
        if (args.x === undefined || args.y === undefined) {
          throw new Error("tap requiere x e y")
        }
        return await run([A, "shell", "input", "tap", String(args.x), String(args.y)])
      }
    }
  },
})
