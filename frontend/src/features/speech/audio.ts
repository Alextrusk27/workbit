import workletUrl from './pcmWorklet.ts?worker&url'

export const SAMPLE_RATE = 16000

export interface MicRecorder {
  stop: () => Promise<void>
}

export class UnsupportedBrowserError extends Error {}

export async function startMicRecorder(
  onFrame: (pcm: ArrayBuffer) => void,
): Promise<MicRecorder> {
  if (!navigator.mediaDevices?.getUserMedia || !window.AudioWorkletNode) {
    throw new UnsupportedBrowserError('Microphone capture is not supported')
  }

  const stream = await navigator.mediaDevices.getUserMedia({
    audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
  })

  const context = new AudioContext({ sampleRate: SAMPLE_RATE })
  try {
    if (context.state === 'suspended') await context.resume()
    await context.audioWorklet.addModule(workletUrl)
  } catch (error) {
    stream.getTracks().forEach((track) => track.stop())
    await context.close()
    throw error
  }

  const source = context.createMediaStreamSource(stream)
  const encoder = new AudioWorkletNode(context, 'pcm-encoder')
  encoder.port.onmessage = (event: MessageEvent<ArrayBuffer>) =>
    onFrame(event.data)

  const mute = context.createGain()
  mute.gain.value = 0
  source.connect(encoder)
  encoder.connect(mute)
  mute.connect(context.destination)

  return {
    stop: async () => {
      encoder.port.onmessage = null
      source.disconnect()
      encoder.disconnect()
      mute.disconnect()
      stream.getTracks().forEach((track) => track.stop())
      await context.close()
    },
  }
}
