// js/chat-agent-controller.js

/**
 * 智能助手主控制器
 */
const ChatAgentController = {
    init() {
        this.fetchInitialData();
        this.initializeElements();
        this.bindEvents();
        AgentUI.init();
    },

    /**
     * 获取初始数据，如UserID
     */
     fetchInitialData() {
        fetch('/biz/chat/getUserId')
            .then(response => response.text())
            .then(userId => {
                CHAT_AGENT_CONFIG.USER_ID = userId;
                CHAT_AGENT_CONFIG.CHAT_ID = Date.now();
                console.log("userId", userId)
            })
            .catch(error => {
                console.error('获取用户ID失败:', error);
                alert('获取用户ID失败，请刷新页面重试。');
            });
    },

    initializeElements() {
        this.$chatWindow = $('#chat-agent-window');
        this.$chatButton = $('#chat-agent-button');
        this.$closeButton = $('#chat-agent-close');
        this.$input = $('#chat-agent-input');
        this.$sendButton = $('#chat-agent-send');
    },

    bindEvents() {
        this.$chatButton.on('click', () => this.$chatWindow.fadeToggle());
        this.$closeButton.on('click', () => this.$chatWindow.fadeOut());

        this.$sendButton.on('click', () => this.sendMessage());
        this.$input.on('keypress', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });
        $('#chat-agent-upload-btn').on('click', () => $('#chat-agent-file-input').click());
    },

    sendMessage() {
        const prompt = this.$input.val().trim();
        if (!prompt || ChatAgentState.isLoading) return;

        ChatAgentUtils.setLoading(true);

        const userMessage = {
            id: ChatAgentUtils.generateMessageId(),
            sender: 'user',
            text: prompt
        };
        AgentUI.renderMessage(userMessage);
        this.$input.val('').css('height', 'auto');

        const aiMessage = {
            id: ChatAgentUtils.generateMessageId(),
            sender: 'ai',
        };
        AgentUI.renderMessage(aiMessage);

        setTimeout(() => this.connectToAgent(prompt, aiMessage.id), 100);
    },

    connectToAgent(prompt, aiMessageId) {
        const aiResponseState = {
            text: "",
            currentStep: { name: "开始处理", startTime: Date.now() },
            completedSteps: [],
            references: [],
            isTextPhase: false,
            timerId: null
        };

        const startStepTimer = () => {
            if (aiResponseState.timerId) {
                clearInterval(aiResponseState.timerId);
            }
            aiResponseState.timerId = setInterval(() => {
                const elapsed = (Date.now() - aiResponseState.currentStep.startTime) / 1000;
                AgentUI.updateTimerDisplay(aiMessageId, elapsed.toFixed(1) + 's');
            }, 100);
        };

        const completeCurrentStep = () => {
            if (aiResponseState.currentStep && aiResponseState.currentStep.name) {
                const duration = ((Date.now() - aiResponseState.currentStep.startTime) / 1000).toFixed(1);
                aiResponseState.completedSteps.push({
                    name: aiResponseState.currentStep.name,
                    duration: duration,
                    toolCalls: aiResponseState.currentStep.toolCalls || []
                });
            }
        };

        AgentUI.updateTimeline(aiMessageId, {
            completed: aiResponseState.completedSteps,
            current: { name: aiResponseState.currentStep.name }
        });
        startStepTimer();

        AgentSSE.connect(prompt, {
            onRunAgent: (data) => {
                if (aiResponseState.isTextPhase) return;
                completeCurrentStep();
                aiResponseState.currentStep = {
                    name: "正在调用: " + data.agentName,
                    startTime: Date.now(),
                    toolCalls: []
                };
                AgentUI.updateTimeline(aiMessageId, {
                    completed: aiResponseState.completedSteps,
                    current: { name: aiResponseState.currentStep.name }
                });
                startStepTimer();
            },
            onToolCall: (data) => {
                if (data && data.toolName && aiResponseState.currentStep && !aiResponseState.isTextPhase) {
                    aiResponseState.currentStep.toolCalls.push(data.toolName);
                    AgentUI.updateTimeline(aiMessageId, {
                        completed: aiResponseState.completedSteps,
                        current: aiResponseState.currentStep
                    });
                }
            },
            onText: (data) => {
                if (!aiResponseState.isTextPhase) {
                    aiResponseState.isTextPhase = true;
                    completeCurrentStep();
                    aiResponseState.currentStep = { name: "生成回复", startTime: Date.now() };
                    AgentUI.updateTimeline(aiMessageId, {
                        completed: aiResponseState.completedSteps,
                        current: { name: aiResponseState.currentStep.name }
                    });
                    startStepTimer();
                }
                aiResponseState.text += data.text;
                AgentUI.updateAiText(aiMessageId, aiResponseState.text);
            },
            onReference: (data) => {
                if (aiResponseState.references.includes(data.reference)) return;
                aiResponseState.references.push(data.reference);
                AgentUI.addKnowledgeSource(aiMessageId, data.reference);
            },
            onCallEnd: () => {
                if (aiResponseState.timerId) {
                    clearInterval(aiResponseState.timerId);
                    aiResponseState.timerId = null;
                }
                completeCurrentStep();
                aiResponseState.currentStep = { name: null };
                AgentUI.updateTimeline(aiMessageId, {
                    completed: aiResponseState.completedSteps,
                    current: aiResponseState.currentStep
                });
                ChatAgentUtils.setLoading(false);
            },
            onError: () => {
                if (aiResponseState.timerId) {
                    clearInterval(aiResponseState.timerId);
                }
                AgentUI.showError(aiMessageId, "抱歉，服务暂时不可用。");
                ChatAgentUtils.setLoading(false);
            }
        });
    },
};

/**
 * 初始化智能助手
 */
$(function () {
    ChatAgentController.init();
});