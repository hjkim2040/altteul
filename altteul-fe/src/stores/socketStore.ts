// socketStore.ts
import { create } from 'zustand';
import SockJS from 'sockjs-client';
import { Stomp, Frame, Message, CompatClient } from '@stomp/stompjs';
import socketResponseMessage from 'types/socketResponseMessage';
import useAuthStore from '@stores/authStore';
import { SocketStore } from 'types/types';

const SOCKET_URL =
  import.meta.env.MODE === 'production'
    ? import.meta.env.VITE_SOCKET_URL_PROD
    : import.meta.env.VITE_SOCKET_URL_DEV;

const RECONNECT_DELAY = 5000;
const MAX_RECONNECT_ATTEMPTS = 5;
const HEARTBEAT_INTERVAL = 30000; // 서버 TTL 90초의 1/3
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;

const stopHeartbeat = () => {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
};

const startHeartbeat = (client: CompatClient) => {
  stopHeartbeat(); // 중복 타이머 방지
  heartbeatTimer = setInterval(() => {
    if (client.connected) {
      client.publish({ destination: '/pub/user/heartbeat' });
    }
  }, HEARTBEAT_INTERVAL);
};

export const useSocketStore = create<SocketStore>((set, get) => ({
  client: null,
  connected: false,
  subscriptions: new Map(),
  reconnectAttempts: 0,
  maxReconnectAttempts: MAX_RECONNECT_ATTEMPTS,

  connect: () => {
    const token = useAuthStore.getState().token;
    if (!token) {
      console.warn('No authentication token found');
      return;
    }

    const socket = new SockJS(SOCKET_URL);
    const stompClient = Stomp.over(socket);

    stompClient.configure({
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },

      debug: console.debug,
      reconnectDelay: RECONNECT_DELAY,

      onConnect: (frame: Frame) => {
        // console.log('Connected:', frame);
        set({
          connected: true,
          reconnectAttempts: 0,
        });

        // 연결 성공 시 sessionStorage에 상태 저장
        sessionStorage.setItem('wsConnected', 'true');

        // 저장된 구독 정보가 있다면 복구
        get().restoreSubscriptions();
        startHeartbeat(stompClient);
      },

      onWebSocketClose: () => {
        stopHeartbeat();
      },

      onStompError: (frame: Frame) => {
        console.error('STOMP error:', frame);
        const { reconnectAttempts, maxReconnectAttempts } = get();
        if (reconnectAttempts < maxReconnectAttempts) {
          set({ reconnectAttempts: reconnectAttempts + 1 });
          setTimeout(() => get().connect(), RECONNECT_DELAY);
        } else {
          console.error('Max reconnection attempts reached');
          get().resetConnection();
        }
      },
    });

    stompClient.activate();
    set({ client: stompClient });
  },

  disconnect: () => {
    const { client } = get();
    if (client && client.connected) {
      client.deactivate();
    }
    get().resetConnection();
    sessionStorage.removeItem('wsConnected');
  },

  resetConnection: () => {
    stopHeartbeat();
    set({
      client: null,
      connected: false,
      subscriptions: new Map(),
      reconnectAttempts: 0,
    });
  },

  subscribe: (destination: string, callback: (data: socketResponseMessage) => void) => {
    // console.log('구독 신청 경로', destination);
    const { client, connected, subscriptions } = get();

    //소켓 연결 자체가 안되어 있으면 종료
    if (!client || !connected) {
      console.warn('No active connection');
      return;
    }

    // 이미 구독중이면 종료
    if (subscriptions.has(destination)) {
      console.warn(`Already subscribed to ${destination}`);
      return;
    }

    // 구독 설정
    const subscription = client.subscribe(destination, (message: Message) => {
      try {
        const data = JSON.parse(message.body);
        callback(data);
      } catch (error) {
        console.error('Error parsing message:', error);
      }
    });

    // 구독 정보 저장
    subscriptions.set(destination, {
      id: subscription.id,
      callback,
    });
    set({ subscriptions: new Map(subscriptions) });

    // sessionStorage에 구독 목록 업데이트
    const activeSubscriptions = JSON.parse(sessionStorage.getItem('wsSubscriptions') || '[]');
    if (!activeSubscriptions.includes(destination)) {
      activeSubscriptions.push(destination);
      sessionStorage.setItem('wsSubscriptions', JSON.stringify(activeSubscriptions));
    }

    // console.log('구독 신청 완료', destination);
  },

  unsubscribe: (destination: string) => {
    const { client, subscriptions } = get();
    const subscription = subscriptions.get(destination);

    if (client && subscription) {
      client.unsubscribe(subscription.id);
      subscriptions.delete(destination);
      set({ subscriptions: new Map(subscriptions) });

      // sessionStorage에서 구독 제거
      const activeSubscriptions = JSON.parse(sessionStorage.getItem('wsSubscriptions') || '[]');
      sessionStorage.setItem(
        'wsSubscriptions',
        JSON.stringify(activeSubscriptions.filter((sub: string) => sub !== destination))
      );
    }

    // console.log('구독 취소 완료', destination);
  },

  sendMessage: (destination: string, message: any) => {
    const { client, connected } = get();

    if (!client || !connected) {
      console.warn('No active connection');
      return;
    }

    client.publish({
      destination,
      body: JSON.stringify(message),
    });
    // console.log('메시지 전송 요청');
  },

  unsubscribeAll: () => {
    const { subscriptions, client } = get();

    if (!client) {
      console.warn('No active connection');
      return;
    }

    // 현재 저장된 모든 구독을 순회하며 구독 해제
    subscriptions.forEach((_, destination) => {
      client.unsubscribe(subscriptions.get(destination)?.id);
    });

    // 상태 초기화
    set({ subscriptions: new Map() });

    // sessionStorage에서도 모든 구독 제거
    sessionStorage.removeItem('wsSubscriptions');

    // console.log('모든 구독 취소 완료');
  },

  restoreSubscriptions: () => {
    const activeSubscriptions = JSON.parse(sessionStorage.getItem('wsSubscriptions') || '[]');
    activeSubscriptions.forEach((destination: string) => {
      // 이미 구독 중인지 체크하고, 그렇다면 구독하지 않도록 처리
      if (!get().subscriptions.has(destination)) {
        get().subscribe(destination, () => {});
      }
    });
  },
}));
