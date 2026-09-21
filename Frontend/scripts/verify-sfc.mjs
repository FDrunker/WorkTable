/**
 * 单文件组件（SFC）校验脚本。
 *
 * 存在的理由：本项目的沙箱环境禁止程序打开命名管道，esbuild 无法 spawn 子进程
 * （vite build 报 spawn EPERM），因此无法在本机完成完整前端构建。
 * 这个脚本用 Vue 官方编译器直接在进程内校验 SFC 的模板与脚本语法，
 * 覆盖"模板表达式写错、script setup 语法错误、导入路径不存在"这类最常见问题。
 *
 * 用法：node scripts/verify-sfc.mjs <文件...>
 */
import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname, extname } from 'node:path'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'

/**
 * 校验单个 .ts 文件：用 TypeScript 编译器 API 做语法分析。
 *
 * 刻意不用 esbuild：即使调用 transform API，它底层仍需要启动服务进程，
 * 在本沙箱里会因禁止命名管道而报 spawn EPERM。
 * typescript 的 transpileModule 完全在进程内完成，不 spawn 任何子进程。
 */
async function verifyTs(abs, errors) {
  try {
    const ts = (await import('typescript')).default
    const source = readFileSync(abs, 'utf8')
    const result = ts.transpileModule(source, {
      fileName: abs,
      reportDiagnostics: true,
      compilerOptions: {
        target: ts.ScriptTarget.ESNext,
        module: ts.ModuleKind.ESNext,
        isolatedModules: true
      }
    })
    const diags = result.diagnostics || []
    for (const d of diags) {
      // 只报语法级错误；类型错误交给 vue-tsc，这里不重复
      if (d.category === ts.DiagnosticCategory.Error) {
        const msg = ts.flattenDiagnosticMessageText(d.messageText, ' ')
        let where = ''
        if (d.file && d.start != null) {
          const pos = d.file.getLineAndCharacterOfPosition(d.start)
          where = ` (第 ${pos.line + 1} 行)`
        }
        errors.push(`TS 语法${where}: ${msg}`)
      }
    }
  } catch (e) {
    errors.push(`TS 分析失败: ${e.message}`)
  }
}

const files = process.argv.slice(2)
if (files.length === 0) {
  console.error('用法: node scripts/verify-sfc.mjs <文件...>')
  process.exit(2)
}

let failed = 0

for (const file of files) {
  const abs = resolve(file)
  const errors = []
  const isTs = ['.ts', '.mts', '.js', '.mjs'].includes(extname(abs))

  try {
    if (isTs) {
      await verifyTs(abs, errors)
    } else {
      const source = readFileSync(abs, 'utf8')
      const { descriptor, errors: parseErrors } = parse(source, { filename: abs })
      parseErrors.forEach((e) => errors.push(`SFC 解析: ${e.message}`))

      // script setup：语法与宏使用是否正确
      if (descriptor.scriptSetup || descriptor.script) {
        try {
          compileScript(descriptor, { id: abs })
        } catch (e) {
          errors.push(`script 编译: ${e.message}`)
        }
      }

      // template：表达式绑定、指令语法
      if (descriptor.template) {
        const result = compileTemplate({
          source: descriptor.template.content,
          filename: abs,
          id: abs,
          compilerOptions: { mode: 'module' }
        })
        result.errors.forEach((e) => {
          errors.push(`模板: ${typeof e === 'string' ? e : e.message}`)
        })
      }

      // 静态检查相对导入是否存在（编译期不报、运行时才炸的常见问题）
      const scriptContent = (descriptor.scriptSetup?.content || '') + (descriptor.script?.content || '')
      const importRe = /from\s+['"](\.[^'"]+)['"]/g
      let m
      while ((m = importRe.exec(scriptContent)) !== null) {
        const spec = m[1]
        const base = dirname(abs)
        const candidates = [spec, `${spec}.ts`, `${spec}.vue`, `${spec}/index.ts`]
        if (!candidates.some((c) => existsSync(resolve(base, c)))) {
          errors.push(`相对导入不存在: ${spec}`)
        }
      }
    }
  } catch (e) {
    errors.push(`读取失败: ${e.message}`)
  }

  if (errors.length > 0) {
    failed++
    console.error(`✗ ${file}`)
    errors.forEach((e) => console.error(`    ${e}`))
  } else if (isTs) {
    console.log(`✓ ${file}  (TS 语法通过)`)
  } else {
    const sfc = parse(readFileSync(abs, 'utf8'), { filename: abs }).descriptor
    const tplLines = (sfc.template?.content || '').split('\n').length
    const scriptLines = (sfc.scriptSetup?.content || '').split('\n').length
    console.log(`✓ ${file}  (template ${tplLines} 行, script ${scriptLines} 行)`)
  }
}

if (failed > 0) {
  console.error(`\n${failed} 个文件校验失败`)
  process.exit(1)
}
console.log(`\n全部 ${files.length} 个文件校验通过`)
