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

/**
 * UI操作模块 - 负责所有界面相关的操作
 */
const AgentUI = {
    chatBox: null,

    init() {
        this.chatBox = $('#chat-agent-messages');
        // 配置marked.js以支持GFM换行
        if (window.marked) {
            marked.setOptions({
                breaks: true
            });
        }
    },



    /**
     * 渲染一条消息（用户或AI）
     * @param {object} msgData - 消息数据 { id, sender, text }
     * @returns {jQuery} 消息DOM元素
     */
    renderMessage(msgData) {
        const $message = $(`
        <div class="message">
            <div class="avatar"></div>
            <div class="content"></div>
        </div>
        `);

        $message.attr('id', msgData.id).addClass(msgData.sender);
        $message.find('.avatar').html(
            msgData.sender === 'user' ? '<i class="fa fa-user-circle-o"></i>' : '<i class="fa fa-user-md"></i>'
        );

        if (msgData.sender === 'user') {
            $message.find('.content').text(msgData.text);
        } else {
            // 对于AI消息，创建一个包含所有子容器的结构
            const aiContentHtml = `
            <div class="status-timeline" >
                <div class="status-timeline-header">执行过程</div>
                <div class="timeline"></div>
            </div>
            <div class="ai-text-content"></div>
            <div class="knowledge-sources-container" style="display: none">
                <div class="knowledge-sources-header">
                    <i class="fa fa-lightbulb-o"></i>
                    知识来源:
                </div>
                <div class="knowledge-sources-list"></div>
            </div>`;
            $message.find('.content').html(aiContentHtml);
        }


        this.chatBox.append($message);
        this.scrollToBottom();
        return $message;
    },

    /**
     * 更新AI消息的文本内容
     * @param {string} messageId - 消息ID
     * @param {string} text - AI回复的完整文本
     */
    updateAiText(messageId, text) {
        const $message = $('#' + messageId);
        if ($message.length) {
            const $textContent = $message.find('.ai-text-content');
            $textContent.html(marked.parse(text || ''));
            this.scrollToBottom();
        }
    },

    /**
     * 更新时间线显示
     * @param {string} messageId - 消息ID
     * @param {object} timelineState - 时间线状态 { completed: Array<object>, current: object }
     */
    updateTimeline(messageId, timelineState) {
        const $message = $('#' + messageId);
        if (!$message.length) return;

        const $timelineContainer = $message.find('.status-timeline');
        const $timeline = $timelineContainer.find('.timeline');
        $timelineContainer.show();
        $timeline.empty(); // 每次都重新渲染，保证状态正确

        // 内部辅助函数，用于创建工具调用列表的HTML
        const createToolCallsHtml = (toolCalls) => {
            if (!toolCalls || toolCalls.length === 0) {
                return '';
            }
            const itemsHtml = toolCalls.map(toolName =>
                `<div class="tool-call-item">
                    <i class="fa fa-gear"></i>
                    <span>${toolName}</span>
                 </div>`
            ).join('');
            return `<div class="timeline-step-details">${itemsHtml}</div>`;
        };

        // 渲染已完成的步骤
        timelineState.completed.forEach(step => {
            const durationHtml = `<span class="status-timer">${step.duration}s</span>`;
            const toolCallsHtml = createToolCallsHtml(step.toolCalls); // 生成工具列表
            const stepHtml = `
                <div class="completed">
                    <div class="timeline-item-header">
                        ${step.name}${durationHtml}<span class="status-label">已完成</span>
                    </div>
                    ${toolCallsHtml}
                </div>`;
            $timeline.append(stepHtml);
        });

        // 渲染当前步骤
        if (timelineState.current && timelineState.current.name) {
            const timerHtml = `<span class="status-timer current-timer">0.0s</span>`;
            const toolCallsHtml = createToolCallsHtml(timelineState.current.toolCalls); // 生成工具列表
            const currentHtml = `
                <div class="current">
                     <div class="timeline-item-header">
                        ${timelineState.current.name}...${timerHtml}
                     </div>
                    ${toolCallsHtml}
                </div>`;
            $timeline.append(currentHtml);
        }

        this.scrollToBottom();
    },
    /**
     * 只更新当前步骤的秒表显示
     * @param {string} messageId - 消息ID
     * @param {string} elapsedText - 格式化后的耗时文本 (e.g., "1.2s")
     */
    updateTimerDisplay(messageId, elapsedText) {
        const $message = $('#' + messageId);
        if ($message.length) {
            $message.find('.current-timer').text(elapsedText);
        }
    },

    /**
     * 添加知识来源
     * @param {string} messageId - 消息ID
     * @param {string} sourceText - 知识来源文本
     */
    addKnowledgeSource(messageId, sourceText) {
        const $message = $('#' + messageId);
        if (!$message.length) return;

        const $container = $message.find('.knowledge-sources-container');
        const $list = $container.find('.knowledge-sources-list');

        // 使用模板创建新的来源卡片，避免硬编码HTML
        const $card = $(`<div class="knowledge-source-card">
                           <div class="knowledge-source-info">
                             <span class="knowledge-source-text"></span>
                           </div>
                         </div>`);
        $card.find('.knowledge-source-text').text(sourceText);

        $list.append($card);
        $container.show();
        this.scrollToBottom();
    },

    /**
     * 在指定消息中显示错误信息
     * @param {string} messageId
     * @param {string} errorText
     */
    showError(messageId, errorText) {
        const $message = $('#' + messageId);
        if (!$message.length) return;

        const $timeline = $message.find('.status-timeline .timeline');
        if ($timeline.length) {
            // 如果时间线存在，在时间线最后显示错误
            const errorHtml = `<div class="error">${errorText}</div>`;
            $timeline.find('.current').remove(); // 移除正在进行的步骤
            $timeline.append(errorHtml);
        } else {
            // 如果时间线不存在（例如，文本生成阶段出错），直接用错误文本替换内容
            this.updateAiText(messageId, `**错误**: ${errorText}`);
        }
    },


    /**
     * 滚动到底部
     */
    scrollToBottom() {
        this.chatBox.stop().animate({ scrollTop: this.chatBox[0].scrollHeight }, 30);
    }
};

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

/**
 * 工具函数
 */
const ChatAgentUtils = {
    generateMessageId: () => `msg_${Date.now()}`,
    setLoading(loading) {
        ChatAgentState.isLoading = loading;
        $('#chat-agent-send').prop('disabled', loading);
    }
};

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
        // 文件上传功能可以后续实现，目前保持不变
        $('#chat-agent-upload-btn').on('click', () => $('#chat-agent-file-input').click());
    },

    sendMessage() {
        const prompt = this.$input.val().trim();
        if (!prompt || ChatAgentState.isLoading) return;

        ChatAgentUtils.setLoading(true);

        // 1. 渲染用户消息
        const userMessage = {
            id: ChatAgentUtils.generateMessageId(),
            sender: 'user',
            text: prompt
        };
        AgentUI.renderMessage(userMessage);
        this.$input.val('').css('height', 'auto');

        // 2. 创建AI消息占位符
        const aiMessage = {
            id: ChatAgentUtils.generateMessageId(),
            sender: 'ai',
        };
        AgentUI.renderMessage(aiMessage);

        // 3. 连接SSE并处理事件流
        setTimeout(() => this.connectToAgent(prompt, aiMessage.id), 100)

    },

    /**
     * 连接到智能助手服务
     * @param {string} prompt - 用户输入
     * @param {string} aiMessageId - AI消息的ID
     */
    connectToAgent(prompt, aiMessageId) {
        // 使用一个简单的本地对象来跟踪当前AI响应的状态
        const aiResponseState = {
            text: "",
            currentStep: { name: "开始处理", startTime: Date.now() },
            completedSteps: [],
            references: [],
            isTextPhase: false, // 标记是否已进入文本生成阶段
            timerId: null // 用于存放setInterval的ID
        };

        const startStepTimer = () => {
            // 先清除旧的计时器
            if (aiResponseState.timerId) {
                clearInterval(aiResponseState.timerId);
            }
            // 为当前步骤启动新计时器
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
                    toolCalls: aiResponseState.currentStep.toolCalls || [] // 保存该步骤的工具调用
                });
            }
        };

        // 初始状态显示
        AgentUI.updateTimeline(aiMessageId, {
            completed: aiResponseState.completedSteps,
            current: { name: aiResponseState.currentStep.name }
        });
        startStepTimer(); // 启动第一个步骤的计时器

        AgentSSE.connect(prompt, {
            onRunAgent: (data) => {
                if (aiResponseState.isTextPhase) return;

                completeCurrentStep(); // 完成上一个步骤

                // 更新当前步骤
                aiResponseState.currentStep = {
                    name: "正在调用: " + data.agentName,
                    startTime: Date.now(),
                    toolCalls: []
                };

                AgentUI.updateTimeline(aiMessageId, {
                    completed: aiResponseState.completedSteps,
                    current: { name: aiResponseState.currentStep.name }
                });
                startStepTimer(); // 为新步骤重启计时器
            },
            onToolCall: (data) => {
                // 确保当前有一个正在执行的agent步骤
                if (data && data.toolName && aiResponseState.currentStep && !aiResponseState.isTextPhase) {
                    // 将工具调用信息添加到当前步骤的状态中
                    aiResponseState.currentStep.toolCalls.push(data.toolName);
                    // 使用更新后的状态，重新渲染整个时间线
                    AgentUI.updateTimeline(aiMessageId, {
                        completed: aiResponseState.completedSteps,
                        current: aiResponseState.currentStep
                    });
                }
            },

            onText: (data) => {
                if (!aiResponseState.isTextPhase) {
                    aiResponseState.isTextPhase = true;

                    completeCurrentStep(); // 完成最后一个agent步骤

                    // 设置新步骤为“生成回复”
                    aiResponseState.currentStep = {
                        name: "生成回复",
                        startTime: Date.now()
                    };
                    AgentUI.updateTimeline(aiMessageId, {
                        completed: aiResponseState.completedSteps,
                        current: { name: aiResponseState.currentStep.name }
                    });
                    startStepTimer(); // 为“生成回复”步骤启动计时器
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
                // 停止并清除最后的计时器
                if (aiResponseState.timerId) {
                    clearInterval(aiResponseState.timerId);
                    aiResponseState.timerId = null;
                }

                completeCurrentStep(); // 完成最后一步
                aiResponseState.currentStep = { name: null }; // 清空当前步骤

                AgentUI.updateTimeline(aiMessageId, {
                    completed: aiResponseState.completedSteps,
                    current: aiResponseState.currentStep
                });

                ChatAgentUtils.setLoading(false);
            },

            onError: () => {
                // 出错时也要停止计时器
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