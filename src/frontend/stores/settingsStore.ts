import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

// ─── Constants ────────────────────────────────────────────────────────────────

const SOUND_ENABLED_KEY = 'arbitrary_gate_sound_enabled'

// ─── Store ────────────────────────────────────────────────────────────────────

export const useSettingsStore = defineStore('settings', () => {
  /**
   * 音效总开关（默认开启）
   * 与 useSound.ts 中的 soundEnabled 保持同步
   */
  const soundEnabled = ref<boolean>(
    localStorage.getItem(SOUND_ENABLED_KEY) !== 'false'
  )

  // 持久化到 localStorage
  watch(soundEnabled, (val) => {
    localStorage.setItem(SOUND_ENABLED_KEY, String(val))
  })

  /**
   * 切换音效开关
   */
  function toggleSound() {
    soundEnabled.value = !soundEnabled.value
  }

  return {
    soundEnabled,
    toggleSound,
  }
})
