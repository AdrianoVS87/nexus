import { useEffect, useRef, useCallback } from 'react';

interface OrderUpdate {
  orderId: string;
  status: string;
}

export function useOrderWebSocket(onUpdate: (update: OrderUpdate) => void) {
  const wsRef = useRef<WebSocket | null>(null);

  const connect = useCallback(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const ws = new WebSocket(`${protocol}//${window.location.host}/ws/orders`);

    ws.onmessage = (event) => {
      try {
        const update: OrderUpdate = JSON.parse(event.data);
        onUpdate(update);
      } catch {
        console.error('Failed to parse WebSocket message');
      }
    };

    ws.onclose = () => {
      setTimeout(connect, 3000); // Reconnect after 3s
    };

    wsRef.current = ws;
  }, [onUpdate]);

  useEffect(() => {
    connect();
    return () => wsRef.current?.close();
  }, [connect]);
}
