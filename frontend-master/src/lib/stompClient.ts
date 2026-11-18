// ═══════════════════════════════════════════════════════════════════════════
// Cliente WebSocket STOMP Nativo (@stomp/stompjs) + Barramento Local BroadcastChannel
// Conecta em ws://localhost:8080/ws (heartbeat 10000,10000) com JWT no CONNECT
// e também escuta eventos simulados entre abas quando em modo local.
// ═══════════════════════════════════════════════════════════════════════════

import { Client, type IMessage } from '@stomp/stompjs';
import { getStoredAccessToken, isSimulationModeEnabled } from './http';

type TopicHandler<T = unknown> = (payload: T) => void;

const localBus = new EventTarget();
const broadcastChannel =
  typeof BroadcastChannel !== 'undefined'
    ? new BroadcastChannel('orderflow_stomp_bus')
    : null;

if (broadcastChannel) {
  broadcastChannel.onmessage = (ev) => {
    const { destination, payload } = ev.data || {};
    if (destination) {
      localBus.dispatchEvent(
        new CustomEvent(destination, { detail: payload })
      );
    }
  };
}

export function emitSimulatedStompMessage(
  destination: string,
  payload: unknown
): void {
  localBus.dispatchEvent(new CustomEvent(destination, { detail: payload }));
  broadcastChannel?.postMessage({ destination, payload });
}

export class OrderFlowStompManager {
  private client: Client | null = null;
  private subscriptions = new Map<string, Set<TopicHandler>>();
  private statusListeners = new Set<(connected: boolean) => void>();
  private connected = false;

  public connect(): void {
    const token = getStoredAccessToken();
    if (!token) return;

    if (isSimulationModeEnabled() || import.meta.env.VITE_ENABLE_WS !== 'true') {
      this.setConnected(true);
      return;
    }

    if (this.client?.active) {
      return;
    }

    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerURL =
      import.meta.env.VITE_WS_URL ||
      `${wsProtocol}//${window.location.host}/ws`;

    this.client = new Client({
      brokerURL,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,
      onConnect: () => {
        this.setConnected(true);
        for (const [destination, handlers] of this.subscriptions.entries()) {
          this.subscribeInternal(destination, handlers);
        }
      },
      onDisconnect: () => {
        this.setConnected(false);
      },
      onWebSocketClose: () => {
        this.setConnected(false);
      },
      onStompError: () => {
        this.setConnected(false);
      },
    });

    this.client.activate();
  }

  public disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.setConnected(false);
  }

  public subscribe<T = unknown>(
    destination: string,
    handler: TopicHandler<T>
  ): () => void {
    const typedHandler = handler as TopicHandler;
    let set = this.subscriptions.get(destination);
    if (!set) {
      set = new Set();
      this.subscriptions.set(destination, set);
      if (this.client?.connected) {
        this.subscribeInternal(destination, set);
      }
    }
    set.add(typedHandler);

    const domListener = (e: Event) => {
      const custom = e as CustomEvent<T>;
      handler(custom.detail);
    };
    localBus.addEventListener(destination, domListener);

    return () => {
      set?.delete(typedHandler);
      localBus.removeEventListener(destination, domListener);
    };
  }

  public onConnectionChange(listener: (connected: boolean) => void): () => void {
    this.statusListeners.add(listener);
    listener(this.connected || isSimulationModeEnabled());
    return () => {
      this.statusListeners.delete(listener);
    };
  }

  private subscribeInternal(
    destination: string,
    handlers: Set<TopicHandler>
  ): void {
    this.client?.subscribe(destination, (message: IMessage) => {
      try {
        const parsed = JSON.parse(message.body);
        for (const h of handlers) {
          h(parsed);
        }
      } catch {
        // ignora payload inválido
      }
    });
  }

  private setConnected(state: boolean): void {
    this.connected = state;
    for (const l of this.statusListeners) {
      l(state);
    }
  }
}

export const stompManager = new OrderFlowStompManager();
