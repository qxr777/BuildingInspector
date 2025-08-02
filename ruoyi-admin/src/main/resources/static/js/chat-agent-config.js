// js/chat-agent-config.js

// API和用户ID配置
const CHAT_AGENT_CONFIG = {
    API_URL: 'http://59.110.81.142:8081/api-ai/chat-stream',
    // API_URL: 'http://localhost:8081/api-ai/chat-stream',
    CHAT_ID: '',
    USER_ID: ''
};

// 全局状态
const ChatAgentState = {
    isLoading: false,
    messages: [] // 只存储已完成的消息对象
};

// 工具函数
const ChatAgentUtils = {
    generateMessageId: () => `msg_${Date.now()}`,
    setLoading(loading) {
        ChatAgentState.isLoading = loading;
        $('#chat-agent-send').prop('disabled', loading);
    }
};