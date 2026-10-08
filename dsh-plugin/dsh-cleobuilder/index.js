/**
 * dsh-cleobuilder — CLEO 脚本编译插件（GTASA / GTASA Mobile）
 *
 * 工具：
 *   cleo_compile — 把 CLEO 脚本源码编译为 .cs/.csi 字节码（后端：Linux 版 CLEO 编译器 jar，纯 Java）
 *   cleo_kb      — 读取插件内置的完整知识库（语法/结构/函数/参数/写法/注意事项/游戏数据）
 *
 * 安装：把本目录放入 profile 的 node_modules，并在 profile 的 cordis.patch.yml insert 本插件。
 * 后端要求：本机可执行 `java`（JRE 17+）。编译器 jar 与全部数据随插件内置（compiler/ 目录）。
 */
import { defineTool } from '@deepseek-ai/dsh-tools'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'

export const name = 'cleobuilder'
export const inject = ['tools', 'systemPrompt']

const execFileP = promisify(execFile)
const __dirname = path.dirname(fileURLToPath(import.meta.url))
const JAR = path.join(__dirname, 'compiler', 'cleo-compiler.jar')
const DATA = path.join(__dirname, 'compiler', 'data')
const KB = path.join(__dirname, 'CLEO-KNOWLEDGE-BASE.md')
const KB_RAW = fs.readFileSync(KB, 'utf8')

function defaultOutDir() {
  return path.join(os.homedir(), '.dsh', 'cleo_builds')
}

function safeName(name) {
  return String(name || 'script').replace(/[^\w.\-]+/g, '_')
}

/** 知识库按 "## N. 标题" 切分 */
function kbSections() {
  const sections = []
  const re = /^## ([^\n]+)$/gm
  let m, lastIdx = 0
  while ((m = re.exec(KB_RAW))) {
    if (sections.length > 0) sections[sections.length - 1].end = m.index
    sections.push({ title: m[1], start: m.index, end: KB_RAW.length })
  }
  return sections.map((s) => ({ title: s.title.trim(), body: KB_RAW.slice(s.start, s.end).trim() }))
}

function kbTableOfContents() {
  const secs = kbSections()
  return `CLEO 知识库（内置，${Math.round(KB_RAW.length / 1024)} KB）章节：\n` +
    secs.map((s, i) => `${i + 1}. ${s.title}`).join('\n') +
    `\n\n用 cleo_kb 的 section 参数（章节名关键词，如 语法/函数/车辆/触摸）请求具体章节；加 section=全部 返回全文。`
}

function findSection(query) {
  const q = String(query || '').trim().toLowerCase()
  if (!q) return null
  const secs = kbSections()
  if (q === '全部' || q === 'all' || q === '全文') return { all: true, body: KB_RAW }
  const exact = secs.find((s) => s.title.toLowerCase().includes(q))
  if (exact) return { all: false, body: exact.body, title: exact.title }
  // 标题之外的关键词
  const hits = secs.filter((s) => s.body.toLowerCase().includes(q))
  if (hits.length === 0) return { all: false, body: '', title: null }
  const pick = hits[0]
  return { all: false, body: pick.body, title: pick.title }
}

export function apply(ctx, config) {
  const outDir = config?.outDir || defaultOutDir()
  try { fs.mkdirSync(outDir, { recursive: true }) } catch {}

  ctx.systemPrompt.section({
    name: 'tools:cleo',
    order: 110,
    text: () => `制作 CLEO 脚本（GTA SA / GTASA Mobile)时：先用 cleo_kb 查内置知识库（语法/函数/参数/游戏数据，支持按节查询），再用 cleo_compile 编译验证（产物为 .cs/.csi 字节码，失败时返回带行号的错误）。工具与内置数据保证可用，无需联网。`
  })

  ctx.tools.register(defineTool({
    name: 'cleo_compile',
    description: `把 CLEO 脚本源码编译为 .cs/.csi 字节码文件（GTA SA / GTASA Mobile）。输入脚本源码（含 \\{$CLEO .cs\\} 头与 0000: 标签），返回产物文件路径、字节数与校验和；编译失败返回带行号的错误。`,
    parameters: {
      source: {
        type: 'string',
        required: true,
        description: 'CLEO 脚本完整源码，示例：\\n{$CLEO .cs}\\n0000:\\n0ACD: show_text_highpriority \'CLEOOK\' time 2000\\n0A93: terminate_this_custom_script'
      },
      game: {
        type: 'string',
        enum: ['gtasa', 'gtasa_mobile'],
        description: '目标模式：gtasa（PC，默认）/ gtasa_mobile（Android：含 0A51 触摸控件、0DE0 九宫格触摸、标准 CLEO 4 全表）'
      },
      name: {
        type: 'string',
        description: '输出文件名（不含扩展名；默认 script）'
      }
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          ok: { type: 'boolean', required: true },
          error: { type: 'string' },
          outfile: { type: 'string' },
          bytes: { type: 'integer' },
          sha256: { type: 'string' },
          mode: { type: 'string' }
        }
      }
    },
    timeoutMs: 60000,
    isConcurrencySafe: () => true,
    async execute(args, exec) {
      const mode = args?.game === 'gtasa_mobile' ? 'gtasa_mobile' : 'gtasa'
      const name = safeName(args?.name || 'script')
      const ext = 'cs' // 顶层容器统一 .cs（内容由头决定）
      const tmpSrc = path.join(os.tmpdir(), `cleo_src_${process.pid}_${Date.now()}_${Math.random().toString(36).slice(2)}.cs`)
      const outFile = path.join(outDir, `${name}.${ext}`)
      try {
        fs.writeFileSync(tmpSrc, args?.source ?? '', 'utf8')
        await execFileP('java', ['-Dfile.encoding=UTF-8', '-jar', JAR, '--mode', mode, '--data', DATA, tmpSrc, outFile], { timeout: 50000, maxBuffer: 4 * 1024 * 1024 })
        const buf = fs.readFileSync(outFile)
        return {
          ok: true,
          outfile: outFile,
          bytes: buf.length,
          sha256: crypto.createHash('sha256').update(buf).digest('hex').slice(0, 16),
          mode
        }
      } catch (e) {
        const stderr = String(e?.stderr || '').trim()
        const message = stderr || String(e?.message || e)
        return { ok: false, error: message, mode }
      } finally {
        try { fs.unlinkSync(tmpSrc) } catch {}
      }
    },
    presentCall: (args) => {
      const src = String(args?.source || '')
      const firstLine = src.split('\n').find((l) => l.trim()) || ''
      return { card: 'generic', title: `cleo_compile (${args?.game || 'gtasa'})`, kind: 'code', rawInput: firstLine.slice(0, 80) }
    },
    presentResult: (args, result) => {
      if (result?.isError) return void 0
      return { card: 'generic', kind: result?.ok ? 'success' : 'error', title: result?.ok ? `编译成功 → ${result.outfile}` : `编译失败：${result?.error}`, rawInput: '' }
    }
  }))

  ctx.tools.register(defineTool({
    name: 'cleo_kb',
    description: `读取内置 CLEO 知识库：语法、脚本结构、支持的函数（opcode 全表）、参数类型、写法示例、注意事项、游戏数据（车辆/人物/武器/按键/坐标）。无参数返回章节目录；section 传章节关键词请求具体内容。`,
    parameters: {
      section: {
        type: 'string',
        description: '可选：章节关键词（如 语法/函数/车辆/武器/触摸/注意事项），或 "全部" 返回全文。缺省返回目录。'
      }
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          content: { type: 'string' },
          title: { type: 'string' }
        }
      }
    },
    timeoutMs: 30000,
    isConcurrencySafe: () => true,
    async execute(args) {
      const q = args?.section
      if (!q || !String(q).trim()) return { title: 'CLEO 知识库目录', content: kbTableOfContents() }
      const found = findSection(String(q))
      if (found.all) {
        const head = KB_RAW.split('\n').slice(0, 12).join('\n')
        return { title: 'CLEO 知识库全文（节选）', content: `${head}\n...\n（完整 ${KB_RAW.length} 字节由代码库 docs/CLEO-KNOWLEDGE-BASE.md 提供；建议用 section 按章节查询）` }
      }
      if (!found.body) return { title: '未命中', content: `知识库中没有匹配 "${q}" 的章节。可用 cleo_kb 查看目录。` }
      const MAX = 12000
      const body = found.body.length > MAX ? found.body.slice(0, MAX) + '\n...（已截断，可缩小关键词再查）' : found.body
      return { title: found.title, content: body }
    },
    presentCall: (args) => ({ card: 'generic', title: `cleo_kb (${args?.section || '目录'})`, kind: 'book', rawInput: String(args?.section || '') })
  }))
}
