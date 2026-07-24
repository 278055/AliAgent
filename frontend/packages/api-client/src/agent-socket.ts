export class AgentSocket {
  private socket?: WebSocket; private stopped = false; private attempts = 0; private sequence = 0
  constructor(private readonly url: string, private readonly onMessage: (message: unknown) => void, private readonly catchUp: (sequence: number) => Promise<void>) {}
  connect() { this.stopped = false; this.open() }
  stop() { this.stopped = true; this.socket?.close() }
  private open() { this.socket = new WebSocket(this.url); this.socket.onmessage = event => { const data = JSON.parse(event.data); this.sequence = Math.max(this.sequence, data.sequence ?? 0); this.onMessage(data) }; this.socket.onclose = () => this.reconnect() }
  private reconnect() { if (this.stopped) return; const delay = Math.min(10_000, 500 * 2 ** this.attempts++); window.setTimeout(async () => { await this.catchUp(this.sequence); if (!this.stopped) this.open() }, delay) }
}
