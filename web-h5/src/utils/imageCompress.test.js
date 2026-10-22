import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { compressImage } from './imageCompress.js'

/** 造一个假的 File */
function makeFile(name, type, size) {
  const f = new File(['x'], name, { type })
  Object.defineProperty(f, 'size', { value: size, configurable: true })
  return f
}

/** 让 Image 在设置 src 后立刻以给定宽高触发 onload */
function stubImage(width, height) {
  const orig = globalThis.Image
  globalThis.Image = class {
    constructor() {
      this.naturalWidth = width
      this.naturalHeight = height
      this.width = width
      this.height = height
    }
    set src(_v) {
      // 异步触发，贴近真实行为
      queueMicrotask(() => this.onload && this.onload())
    }
  }
  return () => {
    globalThis.Image = orig
  }
}

/** 让 canvas.toBlob 返回指定字节数的 blob */
function stubCanvas(blobSize) {
  const origCreate = document.createElement.bind(document)
  const spy = vi.spyOn(document, 'createElement').mockImplementation((tag) => {
    if (tag === 'canvas') {
      return {
        width: 0,
        height: 0,
        getContext: () => ({ fillStyle: '', fillRect: vi.fn(), drawImage: vi.fn() }),
        toBlob: (cb) => cb(new Blob([new Uint8Array(blobSize)], { type: 'image/jpeg' })),
      }
    }
    return origCreate(tag)
  })
  return () => spy.mockRestore()
}

describe('compressImage', () => {
  let restoreImg
  let restoreCanvas

  beforeEach(() => {
    // jsdom 没有 createObjectURL
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:fake')
    globalThis.URL.revokeObjectURL = vi.fn()
  })

  afterEach(() => {
    restoreImg?.()
    restoreCanvas?.()
    vi.restoreAllMocks()
  })

  it('非图片文件原样返回', async () => {
    const f = makeFile('a.txt', 'text/plain', 9999)
    expect(await compressImage(f)).toBe(f)
  })

  it('GIF 跳过压缩（保动图）', async () => {
    const f = makeFile('a.gif', 'image/gif', 5 * 1024 * 1024)
    expect(await compressImage(f)).toBe(f)
  })

  it('小体积 JPEG 直接返回，不解码', async () => {
    const f = makeFile('small.jpg', 'image/jpeg', 50 * 1024)
    const createSpy = vi.spyOn(document, 'createElement')
    expect(await compressImage(f)).toBe(f)
    expect(createSpy).not.toHaveBeenCalledWith('canvas')
  })

  it('超大尺寸图片被缩放并重编码为 JPEG', async () => {
    restoreImg = stubImage(4000, 3000)
    restoreCanvas = stubCanvas(200 * 1024)
    const f = makeFile('big.jpg', 'image/jpeg', 6 * 1024 * 1024)
    const out = await compressImage(f)
    expect(out).not.toBe(f)
    expect(out.type).toBe('image/jpeg')
    expect(out.name).toBe('big.jpg')
    expect(out.size).toBe(200 * 1024)
  })

  it('尺寸不超但体积超阈值的 PNG 也会被重编码', async () => {
    restoreImg = stubImage(1200, 900) // 小于 1600，不触发缩放
    restoreCanvas = stubCanvas(150 * 1024)
    const f = makeFile('shot.png', 'image/png', 3 * 1024 * 1024)
    const out = await compressImage(f)
    expect(out).not.toBe(f)
    expect(out.type).toBe('image/jpeg')
    // PNG 重编码后扩展名改为 .jpg
    expect(out.name).toBe('shot.jpg')
  })

  it('重编码后更大则回退原文件', async () => {
    restoreImg = stubImage(1000, 800)
    restoreCanvas = stubCanvas(900 * 1024) // 比原文件大
    const f = makeFile('x.jpg', 'image/jpeg', 800 * 1024)
    expect(await compressImage(f)).toBe(f)
  })

  it('图片解码失败时安全回退原文件', async () => {
    const orig = globalThis.Image
    globalThis.Image = class {
      set src(_v) {
        queueMicrotask(() => this.onerror && this.onerror())
      }
    }
    const f = makeFile('broken.jpg', 'image/jpeg', 2 * 1024 * 1024)
    expect(await compressImage(f)).toBe(f)
    globalThis.Image = orig
  })

  it('自定义 maxEdge 生效', async () => {
    restoreImg = stubImage(3000, 3000)
    restoreCanvas = stubCanvas(100 * 1024)
    const f = makeFile('big.jpg', 'image/jpeg', 5 * 1024 * 1024)
    const out = await compressImage(f, { maxEdge: 800 })
    expect(out).not.toBe(f)
    expect(out.size).toBe(100 * 1024)
  })
})
