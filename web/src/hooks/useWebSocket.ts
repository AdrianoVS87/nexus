import { useEffect, useRef, useCallback } from 'react';

interface OrderUpdate {
  orderId: string;
  status: string;
}

const MAX_RECONNECT_DELAY_MS = 60_000;
const BASE_DELAY_MS = 3_000;
const MAX_RETRIES = 10;

/**
 * Subscribes to real-time order status updates via WebSocket.
 *
 * When {@link orderId} is provided, the server filters broadcasts
 * to that specific order. Without it, all updates are received.
 *
 * Reconnects with exponential backoff up to {@link MAX_RETRIES}.
 */
export function useOrderWebSocket(
  onUpdate: (update: OrderUpdate) => void,
  orderId?: string,
) {
  const wsRef = useRef<WebSocket | null>(null);
  const retriesRef = useRef(0);

  const connect = useCallback(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const query = orderId ? `?orderId=${orderId}` : '';
    const ws = new WebSocket(`${protocol}//${window.location.host}/ws/orders${query}`);

    ws.onopen = () => {
      retriesRef.current = 0;
    };

    ws.onmessage = (event) => {
      try {
        const update: OrderUpdate = JSON.parse(event.data);
        onUpdate(update);
      } catch {
        console.error('Failed to parse WebSocket message');
      }
    };

    ws.onclose = () => {
      if (retriesRef.current >= MAX_RETRIES) return;

      const delay = Math.min(
        BASE_DELAY_MS * Math.pow(2, retriesRef.current),
        MAX_RECONNECT_DELAY_MS,
      );
      retriesRef.current += 1;
      setTimeout(connect, delay);
    };

    wsRef.current = ws;
  }, [onUpdate, orderId]);

  useEffect(() => {
    connect();
    return () => wsRef.current?.close();
  }, [connect]);
}
