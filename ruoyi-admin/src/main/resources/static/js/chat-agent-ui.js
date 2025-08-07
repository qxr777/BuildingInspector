// js/chat-agent-ui.js

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
            const errorHtml = `<div class="error">${errorText}</div>`;
            $timeline.find('.current').remove(); // 移除正在进行的步骤
            $timeline.append(errorHtml);
        } else {
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