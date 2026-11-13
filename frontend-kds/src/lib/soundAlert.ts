// ═══════════════════════════════════════════════════════════════════════════
// Sintetizador Acústico Web Audio API para Cozinha / KDS
// Dispara alertas sonoros distintos para Novo Pedido, Pedido Pronto e Refazer
// sem depender de arquivos MP3 externos.
// ═══════════════════════════════════════════════════════════════════════════

let audioCtx: AudioContext | null = null;

function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  if (!audioCtx) {
    const Ctx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext })
        .webkitAudioContext;
    if (Ctx) {
      audioCtx = new Ctx();
    }
  }
  if (audioCtx && audioCtx.state === 'suspended') {
    audioCtx.resume().catch(() => {});
  }
  return audioCtx;
}

function playTone(
  freq: number,
  startOffsetSec: number,
  durationSec: number,
  type: OscillatorType = 'sine',
  gainPeak = 0.18
) {
  const ctx = getAudioContext();
  if (!ctx) return;

  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, ctx.currentTime + startOffsetSec);

  gain.gain.setValueAtTime(0.0001, ctx.currentTime + startOffsetSec);
  gain.gain.exponentialRampToValueAtTime(
    gainPeak,
    ctx.currentTime + startOffsetSec + 0.02
  );
  gain.gain.exponentialRampToValueAtTime(
    0.0001,
    ctx.currentTime + startOffsetSec + durationSec
  );

  osc.connect(gain);
  gain.connect(ctx.destination);

  osc.start(ctx.currentTime + startOffsetSec);
  osc.stop(ctx.currentTime + startOffsetSec + durationSec + 0.02);
}

/**
 * Toque duplo de campainha ao entrar um novo pedido CONFIRMADO na fila do KDS.
 */
export function playNewOrderBell(soundEnabled = true): void {
  if (!soundEnabled) return;
  try {
    playTone(587.33, 0, 0.22, 'triangle', 0.22); // D5
    playTone(880.0, 0.18, 0.35, 'triangle', 0.22); // A5
  } catch {
    // ignora caso o navegador bloqueie áudio antes de interação
  }
}

/**
 * Acorde ascendente quando o pedido é marcado como PRONTO para retirada/balcão.
 */
export function playOrderReadyChime(soundEnabled = true): void {
  if (!soundEnabled) return;
  try {
    playTone(523.25, 0, 0.16, 'sine', 0.2); // C5
    playTone(659.25, 0.14, 0.16, 'sine', 0.2); // E5
    playTone(783.99, 0.28, 0.16, 'sine', 0.2); // G5
    playTone(1046.5, 0.42, 0.4, 'sine', 0.25); // C6
  } catch {
    // ignora
  }
}

/**
 * Alerta de atenção quando um pedido retorna para REFAZER (correção).
 */
export function playRemakeAlertBeep(soundEnabled = true): void {
  if (!soundEnabled) return;
  try {
    playTone(440, 0, 0.18, 'sawtooth', 0.15);
    playTone(349.23, 0.2, 0.28, 'sawtooth', 0.15);
  } catch {
    // ignora
  }
}
