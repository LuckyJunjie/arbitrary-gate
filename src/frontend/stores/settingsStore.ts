import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

// ─── Constants ────────────────────────────────────────────────────────────────

const SOUND_ENABLED_KEY = 'arbitrary_gate_sound_enabled'
const SOUND_VOLUME_KEY = 'arbitrary_gate_sound_volume'
const MUSIC_ENABLED_KEY = 'arbitrary_gate_music_enabled'
const MUSIC_VOLUME_KEY = 'arbitrary_gate_music_volume'
const VIBRATION_ENABLED_KEY = 'arbitrary_gate_vibration_enabled'
const NARRATIVE_SPEED_KEY = 'arbitrary_gate_narrative_speed'
const STORY_UPDATE_PUSH_KEY = 'arbitrary_gate_story_update_push'
const DAILY_FORTUNE_REMINDER_KEY = 'arbitrary_gate_daily_fortune_reminder'

export type NarrativeSpeed = 'slow' | 'medium' | 'fast'

// ─── Store ────────────────────────────────────────────────────────────────────

export const useSettingsStore = defineStore('settings', () => {
  // ── 音效 ──────────────────────────────────────────────────────────────────

  /**
   * 音效总开关（默认开启）
   */
  const soundEnabled = ref<boolean>(
    localStorage.getItem(SOUND_ENABLED_KEY) !== 'false'
  )

  /**
   * 音效音量 (0.0 – 1.0)
   */
  const soundVolume = ref<number>(
    parseFloat(localStorage.getItem(SOUND_VOLUME_KEY) ?? '0.8')
  )

  // ── 音乐 ──────────────────────────────────────────────────────────────────

  /**
   * 背景音乐开关（默认开启）
   */
  const musicEnabled = ref<boolean>(
    localStorage.getItem(MUSIC_ENABLED_KEY) !== 'false'
  )

  /**
   * 背景音乐音量 (0.0 – 1.0)
   */
  const musicVolume = ref<number>(
    parseFloat(localStorage.getItem(MUSIC_VOLUME_KEY) ?? '0.6')
  )

  // ── 触感 ──────────────────────────────────────────────────────────────────

  /**
   * 震动反馈开关（默认开启）
   */
  const vibrationEnabled = ref<boolean>(
    localStorage.getItem(VIBRATION_ENABLED_KEY) !== 'false'
  )

  // ── 叙事 ──────────────────────────────────────────────────────────────────

  /**
   * 叙事语速：慢 / 中 / 快
   */
  const narrativeSpeed = ref<NarrativeSpeed>(
    (localStorage.getItem(NARRATIVE_SPEED_KEY) as NarrativeSpeed) ?? 'medium'
  )

  // ── 通知 ──────────────────────────────────────────────────────────────────

  /**
   * 故事更新推送开关（默认开启）
   */
  const storyUpdatePushEnabled = ref<boolean>(
    localStorage.getItem(STORY_UPDATE_PUSH_KEY) !== 'false'
  )

  /**
   * 每日运势提醒开关（默认关闭）
   */
  const dailyFortuneReminderEnabled = ref<boolean>(
    localStorage.getItem(DAILY_FORTUNE_REMINDER_KEY) === 'true'
  )

  // ── 持久化 ────────────────────────────────────────────────────────────────

  watch(soundEnabled, (val) => localStorage.setItem(SOUND_ENABLED_KEY, String(val)))
  watch(soundVolume, (val) => localStorage.setItem(SOUND_VOLUME_KEY, String(val)))
  watch(musicEnabled, (val) => localStorage.setItem(MUSIC_ENABLED_KEY, String(val)))
  watch(musicVolume, (val) => localStorage.setItem(MUSIC_VOLUME_KEY, String(val)))
  watch(vibrationEnabled, (val) => localStorage.setItem(VIBRATION_ENABLED_KEY, String(val)))
  watch(narrativeSpeed, (val) => localStorage.setItem(NARRATIVE_SPEED_KEY, val))
  watch(storyUpdatePushEnabled, (val) => localStorage.setItem(STORY_UPDATE_PUSH_KEY, String(val)))
  watch(dailyFortuneReminderEnabled, (val) => localStorage.setItem(DAILY_FORTUNE_REMINDER_KEY, String(val)))

  // ── Actions ───────────────────────────────────────────────────────────────

  function toggleSound() {
    soundEnabled.value = !soundEnabled.value
  }

  function toggleMusic() {
    musicEnabled.value = !musicEnabled.value
  }

  function toggleVibration() {
    vibrationEnabled.value = !vibrationEnabled.value
  }

  function toggleStoryUpdatePush() {
    storyUpdatePushEnabled.value = !storyUpdatePushEnabled.value
  }

  function toggleDailyFortuneReminder() {
    dailyFortuneReminderEnabled.value = !dailyFortuneReminderEnabled.value
  }

  function setNarrativeSpeed(speed: NarrativeSpeed) {
    narrativeSpeed.value = speed
  }

  return {
    // State
    soundEnabled,
    soundVolume,
    musicEnabled,
    musicVolume,
    vibrationEnabled,
    narrativeSpeed,
    storyUpdatePushEnabled,
    dailyFortuneReminderEnabled,
    // Actions
    toggleSound,
    toggleMusic,
    toggleVibration,
    toggleStoryUpdatePush,
    toggleDailyFortuneReminder,
    setNarrativeSpeed,
  }
})
