import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'

export type CropPreset = 'free' | '1:1' | '16:9' | '4:3'

export interface CropImageOptions {
  defaultPreset?: CropPreset
  title?: string
}

const PRESET_RATIO: Record<CropPreset, number> = {
  free: Number.NaN,
  '1:1': 1,
  '16:9': 16 / 9,
  '4:3': 4 / 3
}

function isGif(file: File) {
  return file.type === 'image/gif' || /\.gif$/i.test(file.name || '')
}

/**
 * 前台单图裁剪：弹窗 + cropperjs。取消返回 null；GIF 跳过裁剪。
 */
export function cropImage(file: File, options: CropImageOptions = {}): Promise<File | null> {
  if (isGif(file)) return Promise.resolve(file)

  return new Promise(resolve => {
    const objectUrl = URL.createObjectURL(file)
    let cropper: Cropper | null = null
    let settled = false

    const overlay = document.createElement('div')
    overlay.className = 'bd-crop-overlay'
    overlay.innerHTML = `
      <div class="bd-crop-modal" role="dialog" aria-modal="true">
        <div class="bd-crop-header">
          <strong>${options.title || '裁剪图片'}</strong>
          <div class="bd-crop-presets">
            <button type="button" data-preset="free">自由</button>
            <button type="button" data-preset="1:1">1:1</button>
            <button type="button" data-preset="16:9">16:9</button>
            <button type="button" data-preset="4:3">4:3</button>
          </div>
        </div>
        <div class="bd-crop-stage"><img alt="裁剪预览" /></div>
        <div class="bd-crop-footer">
          <button type="button" data-action="cancel">取消</button>
          <button type="button" data-action="skip">使用原图</button>
          <button type="button" class="primary" data-action="confirm">确认裁剪</button>
        </div>
      </div>
    `

    const style = document.createElement('style')
    style.textContent = `
      .bd-crop-overlay{position:fixed;inset:0;z-index:4000;background:rgba(0,0,0,.55);display:flex;align-items:center;justify-content:center;padding:12px;touch-action:none}
      .bd-crop-modal{width:min(720px,100%);max-height:min(96vh,920px);background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 20px 60px rgba(0,0,0,.25);display:flex;flex-direction:column}
      .bd-crop-header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 14px;border-bottom:1px solid #f0f0f0;flex-wrap:wrap}
      .bd-crop-presets{display:flex;gap:6px;flex-wrap:wrap}
      .bd-crop-presets button,.bd-crop-footer button{border:1px solid #d9d9d9;background:#fff;border-radius:6px;padding:8px 12px;cursor:pointer;font-size:13px;min-height:36px}
      .bd-crop-presets button.active{border-color:#409eff;color:#409eff;background:#ecf5ff}
      .bd-crop-stage{height:min(52vh,420px);min-height:220px;background:#1f1f1f;position:relative;touch-action:none}
      .bd-crop-stage img{display:block;max-width:100%}
      .bd-crop-stage .cropper-container{width:100%!important;height:100%!important}
      .bd-crop-footer{display:flex;justify-content:flex-end;gap:8px;padding:12px 14px;border-top:1px solid #f0f0f0;flex-wrap:wrap}
      .bd-crop-footer .primary{background:#409eff;border-color:#409eff;color:#fff}
      @media (max-width:640px){.bd-crop-header{flex-direction:column;align-items:flex-start}.bd-crop-stage{height:min(46vh,340px)}.bd-crop-footer{justify-content:stretch}.bd-crop-footer button{flex:1}}
    `

    document.head.appendChild(style)
    document.body.appendChild(overlay)

    const img = overlay.querySelector('img') as HTMLImageElement
    const presetButtons = Array.from(overlay.querySelectorAll<HTMLButtonElement>('[data-preset]'))
    let preset: CropPreset = options.defaultPreset || 'free'

    const markPreset = () => {
      presetButtons.forEach(btn => {
        btn.classList.toggle('active', btn.dataset.preset === preset)
      })
    }
    markPreset()

    const cleanup = (result: File | null) => {
      if (settled) return
      settled = true
      cropper?.destroy()
      cropper = null
      URL.revokeObjectURL(objectUrl)
      overlay.remove()
      style.remove()
      resolve(result)
    }

    const applyPreset = (next: CropPreset) => {
      preset = next
      markPreset()
      cropper?.setAspectRatio(Number.isNaN(PRESET_RATIO[preset]) ? Number.NaN : PRESET_RATIO[preset])
    }

    presetButtons.forEach(btn => {
      btn.addEventListener('click', () => applyPreset((btn.dataset.preset || 'free') as CropPreset))
    })

    overlay.querySelector('[data-action="cancel"]')?.addEventListener('click', () => cleanup(null))
    overlay.querySelector('[data-action="skip"]')?.addEventListener('click', () => cleanup(file))
    overlay.querySelector('[data-action="confirm"]')?.addEventListener('click', () => {
      if (!cropper) {
        cleanup(null)
        return
      }
      const mime = file.type && file.type !== 'image/gif' ? file.type : 'image/jpeg'
      const canvas = cropper.getCroppedCanvas({
        imageSmoothingEnabled: true,
        imageSmoothingQuality: 'high',
        maxWidth: 4096,
        maxHeight: 4096
      })
      if (!canvas) {
        cleanup(null)
        return
      }
      canvas.toBlob(blob => {
        if (!blob) {
          cleanup(null)
          return
        }
        cleanup(new File([blob], file.name || `crop-${Date.now()}.jpg`, { type: mime, lastModified: Date.now() }))
      }, mime, 0.92)
    })

    img.onload = () => {
      cropper = new Cropper(img, {
        viewMode: 1,
        dragMode: 'move',
        autoCropArea: 1,
        responsive: true,
        background: false,
        checkOrientation: true,
        aspectRatio: Number.isNaN(PRESET_RATIO[preset]) ? Number.NaN : PRESET_RATIO[preset]
      })
    }
    img.src = objectUrl
  })
}
