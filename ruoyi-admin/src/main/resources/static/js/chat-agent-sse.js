// js/chat-agent-sse.js

/**
 * SSE事件源模块 - 负责服务器发送事件的处理
 */
const AgentSSE = {
    connect(prompt, callbacks) {
        const url = `${CHAT_AGENT_CONFIG.API_URL}?prompt=${encodeURIComponent(prompt)}&chatId=${encodeURIComponent(CHAT_AGENT_CONFIG.CHAT_ID)}&userId=${encodeURIComponent(CHAT_AGENT_CONFIG.USER_ID)}`;
        const eventSource = new EventSource(url);

        eventSource.addEventListener('runAgent', (event) => callbacks.onRunAgent?.(JSON.parse(event.data)));
        eventSource.addEventListener('toolCall', (event) => callbacks.onToolCall?.(JSON.parse(event.data)));
        eventSource.addEventListener('agentEnd', (event) => callbacks.onAgentEnd?.(JSON.parse(event.data)));
        eventSource.addEventListener('text', (event) => callbacks.onText?.(JSON.parse(event.data)));
        eventSource.addEventListener('reference', (event) => callbacks.onReference?.(JSON.parse(event.data)));

        eventSource.addEventListener('callEnd', () => {
            eventSource.close();
            callbacks.onCallEnd?.();
        });

        eventSource.onerror = (error) => {
            console.error('EventSource Error:', error);
            eventSource.close();
            callbacks.onError?.(error);
        };

        return eventSource;
    }
};