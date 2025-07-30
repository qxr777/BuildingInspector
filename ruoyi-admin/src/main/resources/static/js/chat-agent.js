/**
 * 智能助手配置
 */
let CHAT_ID = ''
let USER_ID = ''

fetch('/biz/chat/getUserId')
    .then(response => response.text())
    .then(userId => {
        USER_ID = userId;
        CHAT_ID = userId;
        CHAT_AGENT_CONFIG.USER_ID = userId;
        CHAT_AGENT_CONFIG.CHAT_ID = userId;
    })
    .catch(error => {
        console.error('获取用户ID失败:', error);
        alert('获取用户ID失败，请刷新页面重试。');
    });


const CHAT_AGENT_CONFIG = {
    // API_URL: 'http://localhost:8081/api-ai/chat-stream',
    API_URL: 'http://47.94.205.90:8081/api-ai/chat-stream',
    WELCOME_MESSAGE: '你好！我是你的智能助手，有什么可以帮你的吗？',
    WELCOME_DELAY: 500,
    CHAT_ID: CHAT_ID,
    USER_ID: USER_ID
};

/**
 * 智能助手全局状态
 */
const ChatAgentState = {
    isLoading: false,
    messages: [],
    timers: new Map(), // 存储计时器的Map，key为messageId_statusIndex
    timelineManagers: new Map() // 存储TimelineManager实例
};

/**
 * 时间线管理器
 */
class TimelineManager {
    constructor(messageId, container) {
        this.messageId = messageId;
        this.container = container;
        this.timeline = [];
        this.timerId = null;
        this.startTime = null;

        this.init();
    }

    init() {
        const template = $('#status-timeline-template').html();
        this.element = $(template);
        this.timelineContainer = this.element.find('.timeline');
        this.container.prepend(this.element);
    }

    addStep(stepName) {
        // 完成上一步
        if (this.timeline.length > 0) {
            const lastStep = this.timeline[this.timeline.length - 1];
            if (!lastStep.isCompleted) {
                lastStep.isCompleted = true;
                lastStep.duration = this.stopTimer();
            }
        }
        // 添加新步骤
        const newStep = { name: stepName, isCompleted: false, duration: null };
        this.timeline.push(newStep);
        this.startTimer();
        this.render();
    }

    completeAll(isSuccess = true) {
        if (this.timeline.length > 0) {
            const lastStep = this.timeline[this.timeline.length - 1];
            if (!lastStep.isCompleted) {
                lastStep.isCompleted = true;
                lastStep.duration = this.stopTimer();
            }
        }
        if (!isSuccess) {
            this.timeline.push({ name: '处理失败', isCompleted: true, duration: 0, isError: true });
        }
        this.render();
    }

    startTimer() {
        this.stopTimer(); // 确保只有一个计时器在运行
        this.startTime = Date.now();
        this.timerId = setInterval(() => {
            this.updateCurrentStepDuration();
        }, 100);
    }

    stopTimer() {
        clearInterval(this.timerId);
        this.timerId = null;
        if (this.startTime) {
            const duration = (Date.now() - this.startTime) / 1000;
            this.startTime = null;
            return duration;
        }
        return 0;
    }

    updateCurrentStepDuration() {
        if (!this.startTime) return;
        const elapsed = (Date.now() - this.startTime) / 1000;
        const $currentTimer = this.timelineContainer.find('.current .status-timer');
        if ($currentTimer.length) {
            $currentTimer.text(ChatAgentUtils.formatDuration(elapsed));
        }
    }

    render() {
        this.timelineContainer.empty();
        this.timeline.forEach(step => {
            let durationText = '';
            if (step.duration !== null) {
                durationText = `<span class="status-timer">${ChatAgentUtils.formatDuration(step.duration)}</span>`;
            }

            let stepHtml;
            if (step.isCompleted) {
                if (step.isError) {
                    stepHtml = `<div class="error">${step.name}</div>`;
                } else {
                    const completedLabel = '<span class="status-label">已完成</span>';
                    stepHtml = `<div class="completed">${step.name}${durationText}${completedLabel}</div>`;
                }
            } else {
                // 当前步骤
                const timerSpan = `<span class="status-timer">0.0s</span>`;
                stepHtml = `<div class="current">${step.name}...${timerSpan}</div>`;
            }
            this.timelineContainer.append(stepHtml);
        });
    }

    cleanup() {
        this.stopTimer();
    }
}


/**
 * UI操作模块 - 负责所有界面相关的操作
 */
const AgentUI = {
    chatBox: null,

    init() {
        this.chatBox = $('#chat-agent-messages');
    },

    /**
     * 渲染消息
     * @param {Object} msgData - 消息数据
     * @returns {jQuery} 消息元素
     */
    renderMessage(msgData) {
        const template = $('#message-item-template').html();
        const $message = $(template);

        $message.attr('id', msgData.id);
        $message.addClass(msgData.sender);

        if (msgData.sender === 'user') {
            $message.find('.avatar').html('<i class="fa fa-user-circle-o"></i>');
        } else {
            $message.find('.avatar').html('<i class="fa fa-user-md"></i>');
        }

        if (msgData.text) {
            this.updateMessageText($message, msgData.text, msgData.sender === 'user');
        }

        this.chatBox.append($message);
        this.scrollToBottom();
        return $message;
    },

    /**
     * 更新消息文本内容
     * @param {jQuery} $message - 消息元素
     * @param {string} text - 文本内容
     * @param {boolean} isUser - 是否为用户消息
     */
    updateMessageText($message, text, isUser) {
        let $content = $message.find('.content');
        if (isUser) {
            $content.text(text);
        } else {

            const $attachmentContainer = $content.find('.attachment-container').detach();
            const $knowledgeContainer = $content.find('.knowledge-sources-container').detach();

            // 更新文本内容
            let $textContent = $content.find('.ai-text-content');
            if (!$textContent.length) {
                $textContent = $('<div class="ai-text-content"></div>');
                $content.append($textContent);
            }

            // 重新添加容器到正确位置
            if ($attachmentContainer.length) {
                $textContent.after($attachmentContainer);
            }
            if ($knowledgeContainer.length) {
                $content.append($knowledgeContainer);
            }

            // 使用 marked.js 解析AI的Markdown回复
            $textContent.html(marked.parse(text || ''));
        }
        this.scrollToBottom();
    },

    /**
     * 更新状态时间线
     * @param {string} messageId - 消息ID
     * @param {Object} statusData - 状态数据
     */
    updateStatusTimeline(messageId, statusData) {
        const $message = $('#' + messageId);
        if (!$message.length) return;

        let manager = ChatAgentState.timelineManagers.get(messageId);
        if (!manager) {
            manager = new TimelineManager(messageId, $message.find('.content'));
            ChatAgentState.timelineManagers.set(messageId, manager);
        }

        if (statusData.isCompleted) {
            manager.completeAll(statusData.isSuccess);
            // 清理，因为任务已结束
            ChatAgentState.timelineManagers.delete(messageId);
        } else if (statusData.currentStatus) {
            manager.addStep(statusData.currentStatus);
        }
    },

    /**
     * 添加附件
     * @param {string} messageId - 消消息ID
     * @param {Object} attachmentData - 附件数据
     */
    addAttachment(messageId, attachmentData) {
        const $message = $('#' + messageId);
        const $content = $message.find('.content');

        // 查找或创建附件容器
        let $attachmentContainer = $content.find('.attachment-container');
        if (!$attachmentContainer.length) {
            $attachmentContainer = $('<div class="attachment-container"><div class="attachment-header">相关附件</div><div class="attachment-list"></div></div>');

            // 将附件容器插入到时间线之后，文本内容之前
            const $timeline = $content.find('.status-timeline');
            const $textContent = $content.find('.ai-text-content');

            if ($timeline.length) {
                $timeline.after($attachmentContainer);
            } else if ($textContent.length) {
                $textContent.before($attachmentContainer);
            } else {
                $content.append($attachmentContainer);
            }
        }

        const $attachmentList = $attachmentContainer.find('.attachment-list');
        const $attachment = $($('#attachment-card-template').html());

        $attachment.attr('href', attachmentData.url).attr('download', attachmentData.name);
        $attachment.find('.attachment-name').text(attachmentData.name);
        $attachmentList.append($attachment);
        this.scrollToBottom();
    },

    /**
     * 添加知识来源到消息
     * @param {string} messageId - 消息ID
     * @param {string} sourceText - 知识来源文本
     */
    addKnowledgeSource(messageId, sourceText) {
        const $message = $('#' + messageId);
        const $content = $message.find('.content');

        // 查找或创建知识来源容器
        let $knowledgeContainer = $content.find('.knowledge-sources-container');
        if (!$knowledgeContainer.length) {
            const knowledgeTemplate = $('#knowledge-sources-template').html();
            $knowledgeContainer = $(knowledgeTemplate);
            
            // 将知识来源容器插入到内容的最后
            $content.append($knowledgeContainer);
        }

        const $knowledgeList = $knowledgeContainer.find('.knowledge-sources-list');
        const $sourceCard = $($('#knowledge-source-card-template').html());

        $sourceCard.find('.knowledge-source-text').text(sourceText);
        $knowledgeList.append($sourceCard);
        this.scrollToBottom();
    },

    /**
     * 滚动到底部
     */
    scrollToBottom() {
        this.chatBox.scrollTop(this.chatBox[0].scrollHeight);
    }
};

/**
 * SSE事件源模块 - 负责服务器发送事件的处理
 */
const AgentSSE = {
    /**
     * 连接SSE事件源
     * @param {string} prompt - 用户输入的提示
     * @param {Object} callbacks - 回调函数集合
     */
    connect(prompt, callbacks) {
        const url = `${CHAT_AGENT_CONFIG.API_URL}?prompt=${encodeURIComponent(prompt)}&chatId=${encodeURIComponent(CHAT_AGENT_CONFIG.CHAT_ID)}&userId=${encodeURIComponent(CHAT_AGENT_CONFIG.USER_ID)}`;
        const eventSource = new EventSource(url);

        eventSource.addEventListener('runAgent', (event) => {
            try {
                const data = JSON.parse(event.data);
                callbacks.onRunAgent?.(data);
            } catch (e) {
                console.error('解析状态事件数据失败:', e);
            }
        });

        eventSource.addEventListener('agentEnd', (event) => {
            try {
                const data = JSON.parse(event.data);
                callbacks.onAgentEnd?.(data);
            } catch (e) {
                console.error('解析状态事件数据失败:', e);
            }
        });

        eventSource.addEventListener('text', (event) => {
            try {
                const data = JSON.parse(event.data);
                callbacks.onText?.(data);
            } catch (e) {
                console.error('解析文本块事件数据失败:', e);
            }
        });

        eventSource.addEventListener('reference', (event) => {
            try {
                const data = JSON.parse(event.data);
                callbacks.onReference?.(data);
            } catch (e) {
                console.error('解析知识来源事件数据失败:', e);
            }
        });

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
    /**
     * 生成消息ID
     */
    generateMessageId() {
        return `msg_${Date.now()}`;
    },

    /**
     * 获取当前时间字符串
     */
    getCurrentTimeString() {
        return new Date().toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    },

    /**
     * 格式化耗时
     * @param {number} seconds - 秒数（可以是小数）
     * @returns {string} 格式化的时间字符串
     */
    formatDuration(seconds) {
        if (seconds < 60) {
            return `${seconds.toFixed(1)}s`;
        } else {
            const minutes = Math.floor(seconds / 60);
            const remainingSeconds = (seconds % 60);
            return `${minutes}m ${remainingSeconds.toFixed(1)}s`;
        }
    },

    /**
     * 启动计时器
     * @param {string} timerId - 计时器ID
     * @param {function} updateCallback - 更新回调函数
     * @returns {object} 计时器对象
     */
    startTimer(timerId, updateCallback) {
        const startTime = Date.now();
        const timer = {
            startTime,
            intervalId: setInterval(() => {
                const elapsed = (Date.now() - startTime) / 1000;
                updateCallback(elapsed);
            }, 100)
        };

        ChatAgentState.timers.set(timerId, timer);
        return timer;
    },

    /**
     * 停止计时器
     * @param {string} timerId - 计时器ID
     * @returns {number} 总耗时（秒，精确到小数点后1位）
     */
    stopTimer(timerId) {
        const timer = ChatAgentState.timers.get(timerId);
        if (timer) {
            clearInterval(timer.intervalId);
            const totalTime = Math.round((Date.now() - timer.startTime) / 100) / 10; // 精确到0.1秒
            ChatAgentState.timers.delete(timerId);
            return totalTime;
        }
        return 0;
    },

    /**
     * 设置加载状态
     * @param {boolean} loading - 是否加载中
     */
    setLoading(loading) {
        ChatAgentState.isLoading = loading;
        $('#chat-agent-input').prop('disabled', loading);
        $('#chat-agent-send').prop('disabled', loading);
    }
};

/**
 * 智能助手主控制器
 */
const ChatAgentController = {
    /**
     * 初始化智能助手
     */
    init() {
        this.initializeElements();
        this.bindEvents();
        this.showWelcomeMessage();
        AgentUI.init();

        // 配置marked.js以支持GFM换行
        if (window.marked) {
            marked.setOptions({
                breaks: true
            });
        }
    },

    /**
     * 初始化DOM元素引用
     */
    initializeElements() {
        this.$chatWindow = $('#chat-agent-window');
        this.$chatButton = $('#chat-agent-button');
        this.$closeButton = $('#chat-agent-close');
        this.$input = $('#chat-agent-input');
        this.$sendButton = $('#chat-agent-send');
        this.$uploadButton = $('#chat-agent-upload-btn');
        this.$fileInput = $('#chat-agent-file-input');
    },

    /**
     * 绑定事件处理器
     */
    bindEvents() {
        // 切换聊天窗口显示
        this.$chatButton.on('click', () => this.$chatWindow.fadeToggle());
        this.$closeButton.on('click', () => this.$chatWindow.fadeOut());

        // 发送消息事件
        this.$sendButton.on('click', () => this.sendMessage());
        this.$input.on('keypress', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });

        // 文件上传事件
        this.$uploadButton.on('click', () => {
            this.$fileInput.click();
        });

        this.$fileInput.on('change', (event) => {
            this.handleFileUpload(event);
        });
    },

    /**
     * 处理文件上传
     * @param {Event} event - 文件选择事件
     */
    handleFileUpload(event) {
        const files = event.target.files;
        if (files.length > 0) {
            // TODO: 实现文件上传功能
            console.log('选择了文件:', files);
            // 这里将来会实现文件上传到服务器的逻辑
            alert(`已选择 ${files.length} 个文件，上传功能待实现`);
        }
    },

    /**
     * 发送消息
     */
    sendMessage() {
        const prompt = this.$input.val().trim();
        if (!prompt || ChatAgentState.isLoading) return;

        ChatAgentUtils.setLoading(true);

        // 渲染用户消息
        const userMessage = {
            id: ChatAgentUtils.generateMessageId(),
            sender: 'user',
            text: prompt
        };
        ChatAgentState.messages.push(userMessage);
        AgentUI.renderMessage(userMessage);
        this.$input.val('').css('height', 'auto');

        // 创建AI消息占位符
        const aiMessage = {
            id: ChatAgentUtils.generateMessageId(),
            sender: 'ai',
            text: '',
            timeline: [],
            isGeneratingText: false // 新增标志，用于跟踪是否进入文本生成阶段
        };
        ChatAgentState.messages.push(aiMessage);
        const $aiMessageEl = AgentUI.renderMessage(aiMessage);

        // 初始化时间线
        const timelineManager = new TimelineManager(aiMessage.id, $aiMessageEl.find('.content'));
        ChatAgentState.timelineManagers.set(aiMessage.id, timelineManager);
        timelineManager.addStep('开始处理');


        // 连接SSE
        this.connectToAgent(prompt, aiMessage);
    },

    /**
     * 连接到智能助手服务
     * @param {string} prompt - 用户输入
     * @param {Object} aiMessage - AI消息对象
     */
    connectToAgent(prompt, aiMessage) {
        const timelineManager = ChatAgentState.timelineManagers.get(aiMessage.id);

        AgentSSE.connect(prompt, {
            onRunAgent: (data) => {
                // 如果已经开始生成文本，则不应再处理agent调用步骤
                if (timelineManager && !aiMessage.isGeneratingText) {
                    timelineManager.addStep("正在调用: " + data.agentName);
                }
            },
            onAgentEnd: (data) => {
                // onAgentEnd 通常表示一个子任务结束，我们可以在这里选择性更新UI，
                // 但主要逻辑在 onRunAgent（开始新任务）和 onCallEnd（全部结束）
                // 当前的 addStep 逻辑已经处理了上一步的完成，所以这里暂时不需要额外操作
            },
            onText: (data) => {
                // 首次收到文本时，标记进入文本生成阶段，并更新时间线
                if (!aiMessage.isGeneratingText) {
                    aiMessage.isGeneratingText = true;
                    if (timelineManager) {
                        // 如果当前时间线中只有"开始处理"步骤，或者最后一个步骤不是agent调用
                        // 那么需要添加"生成回复"步骤
                        const timeline = timelineManager.timeline;
                        const shouldAddStep = timeline.length === 1 || 
                            !timeline[timeline.length - 1].name.includes('正在调用');
                        
                        if (shouldAddStep) {
                            timelineManager.addStep('生成回复');
                        } else {
                            // 如果最后一步是agent调用，直接将其标记为完成并开始生成回复
                            const lastStep = timeline[timeline.length - 1];
                            if (!lastStep.isCompleted) {
                                lastStep.isCompleted = true;
                                lastStep.duration = timelineManager.stopTimer();
                            }
                            // 添加生成回复步骤
                            const newStep = { name: '生成回复', isCompleted: false, duration: null };
                            timeline.push(newStep);
                            timelineManager.startTimer();
                            timelineManager.render();
                        }
                    }
                }
                aiMessage.text += data.text;
                // 确保只更新当前AI消息，避免影响其他消息
                const $targetMessage = $('#' + aiMessage.id);
                if ($targetMessage.length && $targetMessage.hasClass('ai')) {
                    AgentUI.updateMessageText($targetMessage, aiMessage.text, false);
                }
            },
            onAttachment: (data) => {
                // 确保只添加到当前AI消息
                const $targetMessage = $('#' + aiMessage.id);
                if ($targetMessage.length && $targetMessage.hasClass('ai')) {
                    AgentUI.addAttachment(aiMessage.id, data);
                }
            },
            onReference: (data) => {
                // 确保只添加到当前AI消息
                const $targetMessage = $('#' + aiMessage.id);
                if ($targetMessage.length && $targetMessage.hasClass('ai')) {
                    AgentUI.addKnowledgeSource(aiMessage.id, data.reference);
                }
            },
            onCallEnd: () => {
                if (timelineManager) {
                    timelineManager.completeAll(true);
                    ChatAgentState.timelineManagers.delete(aiMessage.id);
                }
                ChatAgentUtils.setLoading(false);
            },
            onError: () => {
                if (timelineManager) {
                    timelineManager.completeAll(false);
                    ChatAgentState.timelineManagers.delete(aiMessage.id);
                }
                // 确保错误消息只更新到当前AI消息
                const $targetMessage = $('#' + aiMessage.id);
                if ($targetMessage.length && $targetMessage.hasClass('ai')) {
                    AgentUI.updateMessageText($targetMessage, '抱歉，服务暂时不可用。', false);
                }
                ChatAgentUtils.setLoading(false);
            }
        });
    },

    /**
     * 显示欢迎消息
     */
    showWelcomeMessage() {
        setTimeout(() => {
            const welcomeMsg = {
                id: `msg_init`,
                sender: 'ai',
                text: CHAT_AGENT_CONFIG.WELCOME_MESSAGE
            };
            ChatAgentState.messages.push(welcomeMsg);
            AgentUI.renderMessage(welcomeMsg);
        }, CHAT_AGENT_CONFIG.WELCOME_DELAY);
    },

    /**
     * 清理所有计时器
     */
    cleanup() {
        ChatAgentState.timers.forEach((timer, timerId) => {
            clearInterval(timer.intervalId);
        });
        ChatAgentState.timers.clear();
        ChatAgentState.timelineManagers.forEach(manager => manager.cleanup());
        ChatAgentState.timelineManagers.clear();
    }
};

/**
 * 页面卸载时清理计时器
 */
$(window).on('beforeunload', () => {
    ChatAgentController.cleanup();
});

/**
 * 初始化智能助手
 * 在页面加载完毕后自动调用
 */
$(function () {
    ChatAgentController.init();
});
