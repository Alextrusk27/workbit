declare const registerProcessor: (name: string, processor: unknown) => void

declare abstract class AudioWorkletProcessor {
  readonly port: MessagePort
}

const FRAME_SAMPLES = 6400

class PcmEncoder extends AudioWorkletProcessor {
  private readonly frame = new Int16Array(FRAME_SAMPLES)
  private offset = 0

  process(inputs: Float32Array[][]) {
    const channel = inputs[0]?.[0]
    if (!channel) return true

    for (let i = 0; i < channel.length; i += 1) {
      const sample = Math.max(-1, Math.min(1, channel[i]))
      this.frame[this.offset] = sample < 0 ? sample * 0x8000 : sample * 0x7fff
      this.offset += 1

      if (this.offset === FRAME_SAMPLES) {
        const copy = this.frame.slice()
        this.port.postMessage(copy.buffer, [copy.buffer])
        this.offset = 0
      }
    }

    return true
  }
}

registerProcessor('pcm-encoder', PcmEncoder)
