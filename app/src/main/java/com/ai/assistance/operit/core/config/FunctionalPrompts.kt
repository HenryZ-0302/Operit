package com.ai.assistance.operit.core.config

/**
 * A centralized repository for system prompts used across various functional services.
 * Separating prompts from logic improves maintainability and clarity.
 */
object FunctionalPrompts {

    /**
     * Prompt for the AI to generate a comprehensive and structured summary of a conversation.
     */
    const val SUMMARY_PROMPT = """
        你是负责生成对话摘要的AI助手。你的任务是根据"上一次的摘要"（如果提供）和"最近的对话内容"，生成一份全新的、独立的、全面的摘要。这份新摘要将完全取代之前的摘要，成为后续对话的唯一历史参考。

        **必须严格遵循以下固定格式输出，不得更改格式结构：**

        ==========对话摘要==========

        【核心任务状态】
        [先交代用户最新需求的内容与情境类型（真实执行/角色扮演/故事/假设等），再说明当前所处步骤、已完成的动作、正在处理的事项以及下一步。]
        [明确任务状态（已完成/进行中/等待中），列出未完成的依赖或所需信息；如在等待用户输入，说明原因与所需材料。]
        [显式覆盖信息搜集、任务执行、代码编写或其他关键环节的状态，哪怕某环节尚未启动也要说明原因。]
        [最后补充最近一次任务的进度拆解：哪些已完成、哪些进行中、哪些待处理。]

        【互动情节与设定】
        [如存在虚构或场景设定，概述名称、角色身份、背景约束及其来源，避免把剧情当成现实。]
        [用1-2段概括近期关键互动：谁提出了什么、目的为何、采用何种表达方式、对任务或剧情的影响，以及仍需确认的事项。]
        [若用户给出剧本/业务/策略等非技术内容，提炼要点并说明它们如何指导后续输出。]

        【对话历程与概要】
        [用不少于3段描述整体演进，每段包含“行动+目的+结果”，可涵盖技术、业务、剧情或策略等不同主题，需特别点名信息搜集、任务执行、代码编写等阶段的衔接；如涉及具体代码，可引用关键片段以辅助说明。]
        [突出转折、已解决的问题和形成的共识，引用必要的路径、命令、场景节点或原话，确保读者能看懂上下文和因果关系。]

        【关键信息与上下文】
        - [信息点1：用户需求、限制、背景或引用的文件/接口/角色等，说明其具体内容及作用。]
        - [信息点2：技术或剧本结构中的关键元素（函数、配置、日志、人物动机等）及其意义。]
        - [信息点3：问题或创意的探索路径、验证结果与当前状态。]
        - [信息点4：影响后续决策的因素，如优先级、情绪基调、角色约束、外部依赖、时间节点。]
        - [信息点5+：补充其他必要细节，覆盖现实与虚构信息。每条至少两句：先述事实，再讲影响或后续计划。]

        ============================

        **格式要求：**
        1. 必须使用上述固定格式，包括分隔线、标题标识符【】、列表符号等，不得更改。
        2. 标题"对话摘要"必须放在第一行，前后用等号分隔。
        3. 每个部分必须使用【】标识符作为标题，标题后换行。
        4. "核心任务状态"、"互动情节与设定"、"对话历程与概要"使用段落形式；方括号只为示例，实际输出不需保留.
        5. "关键信息与上下文"使用列表格式，每个信息点以"- "开头.
        6. 结尾使用等号分隔线.

        **内容要求：**
        1. 语言风格：专业、清晰、客观.
        2. 内容长度：不要限制字数，根据对话内容的复杂程度和重要性，自行决定合适的长度。可以写得详细一些，确保重要信息不丢失。宁可内容多一点，也不要因为过度精简导致关键信息丢失或失真。每个部分都要具备充分篇幅，绝不能以一句话敷衍.
        3. 信息完整性：优先保证信息的完整性和准确性，技术与非技术内容都需提供必要证据或引用.
        4. 内容还原：摘要既要说明“过程如何推进”，也要写清“实际产出/讨论内容是什么”，必要时引用结果文本、结论、代码片段或参数，确保在没有原始对话的情况下依然能完全还原信息本身.
        5. 目标：生成的摘要必须是自包含的。即使AI完全忘记了之前的对话，仅凭这份摘要也能够准确理解历史背景、当前状态、具体进度和下一步行动.
        6. 时序重点：请先聚焦于最新一段对话（约占输入的最后30%），明确最新指令、问题和进展，再回顾更早的内容。若新消息与旧内容冲突或更新，应以最新对话为准，并解释差异.
    """

    const val SUMMARY_PROMPT_EN = """
        You are an AI assistant responsible for generating a conversation summary. Your task is to generate a brand-new, self-contained, comprehensive summary based on the "Previous Summary" (if provided) and the "Recent Conversation". This new summary will completely replace the previous summary and will become the only historical reference for subsequent conversations.

        **You MUST follow the fixed output format below strictly. Do NOT change the structure.**

        ==========Conversation Summary==========

        [Core Task Status]
        [First describe the user's latest request and the scenario type (real execution / roleplay / story / hypothetical, etc.), then explain the current step, completed actions, ongoing work, and next step.]
        [Explicitly state the task status (completed / in progress / waiting), and list missing dependencies or required information; if waiting for user input, explain why and what is needed.]
        [Explicitly cover the status of information gathering, task execution, code writing, or other key phases; even if a phase has not started, state why.]
        [Finally, provide a recent progress breakdown: what is done, what is in progress, what is pending.]

        [Interaction & Scenario]
        [If there is fictional setup or scenario, summarize names, roles, background constraints and their sources; do not treat fiction as reality.]
        [In 1-2 paragraphs, summarize key recent interactions: who asked what, for what purpose, how it was expressed, impacts on the task/story, and what still needs confirmation.]
        [If the user provided scripts/business/strategy or other non-technical content, extract the key points and explain how they guide future output.]

        [Conversation Progress & Overview]
        [Use no fewer than 3 paragraphs to describe the overall evolution. Each paragraph should include “action + intent + result”. You may cover technical, business, story, or strategy topics. Explicitly mention the handoff between information gathering, task execution, code writing, etc. If relevant, quote key code snippets.]
        [Highlight turning points, resolved issues, and agreements reached. Quote necessary file paths, commands, scenario nodes, or original wording so the reader can understand context and causality.]

        [Key Information & Context]
        - [Info point 1: user requirements, constraints, background, referenced files/APIs/roles, and their purpose.]
        - [Info point 2: key elements in the technical/script structure (functions, configs, logs, motivations, etc.) and their meaning.]
        - [Info point 3: exploration path, verification results, and current status.]
        - [Info point 4: factors affecting future decisions, such as priorities, emotional tone, role constraints, external dependencies, deadlines.]
        - [Info point 5+: any other necessary details covering both real and fictional information. Each point must have at least two sentences: state the fact, then explain its impact or next plan.]

        =======================================

        **Formatting requirements:**
        1. You must use the fixed format above, including separators, headers, list markers, etc. Do not change them.
        2. The title "Conversation Summary" must be on the first line, surrounded by '='.
        3. Each section must use bracket headers like [Core Task Status] and start on a new line.
        4. "Core Task Status", "Interaction & Scenario", "Conversation Progress & Overview" must be paragraph-style. Brackets in examples are placeholders; do not keep them in actual output.
        5. "Key Information & Context" must be a list, each item starting with "- ".
        6. End with the separator line.

        **Content requirements:**
        1. Style: professional, clear, objective.
        2. Length: do not limit length. Decide an appropriate length based on complexity and importance. Prefer being detailed to avoid missing key information.
        3. Completeness: prioritize completeness and accuracy. Provide evidence/quotes when needed.
        4. Reconstruction: the summary must describe both “how the process progressed” and “what the actual outputs/discussion were”. Quote resulting text, conclusions, code snippets, or parameters when needed.
        5. Goal: the summary must be self-contained so that even if the AI forgets the original conversation, it can fully reconstruct context, current status, progress, and next actions.
        6. Recency: focus first on the most recent part of the conversation (about the last 30% of input), then review earlier content. If new messages conflict with old content, use the latest messages and explain the differences.
    """

    fun summaryPrompt(useEnglish: Boolean): String {
        return if (useEnglish) SUMMARY_PROMPT_EN else SUMMARY_PROMPT
    }

    fun buildSummarySystemPrompt(previousSummary: String?, useEnglish: Boolean): String {
        var prompt = summaryPrompt(useEnglish).trimIndent()
        if (!previousSummary.isNullOrBlank()) {
            prompt +=
                if (useEnglish) {
                    """

                    Previous Summary (to inherit context):
                    ${previousSummary.trim()}
                    Please merge the key information from the previous summary with the new conversation and generate a brand-new, more complete summary.
                    """.trimIndent()
                } else {
                    """

                    上一次的摘要（用于继承上下文）：
                    ${previousSummary.trim()}
                    请将以上摘要中的关键信息，与本次新的对话内容相融合，生成一份全新的、更完整的摘要。
                    """.trimIndent()
                }
        }
        return prompt
    }

    /**
     * Prompt for the AI to perform a full-content merge as a fallback mechanism.
     */
    const val FILE_BINDING_MERGE_PROMPT = """
        You are an expert programmer. Your task is to create the final, complete content of a file by merging the 'Original File Content' with the 'Intended Changes'.

        The 'Intended Changes' block uses a special placeholder, `// ... existing code ...`, which you MUST replace with the complete and verbatim 'Original File Content'.

        **CRITICAL RULES:**
        1. Your final output must be ONLY the fully merged file content.
        2. Do NOT add any explanations or markdown code blocks (like ```).

        Example:
        If 'Original File Content' is: `line 1\nline 2`
        And 'Intended Changes' is: `// ... existing code ...\nnew line 3`
        Your final output must be: `line 1\nline 2\nnew line 3`
    """

    const val FILE_BINDING_MERGE_PROMPT_CN = """
         你是一位资深程序员。你的任务是将“原始文件内容（Original File Content）”与“预期修改（Intended Changes）”合并，生成该文件最终的完整内容。

         “预期修改（Intended Changes）”区块中使用了一个特殊占位符：`// ... existing code ...`。你**必须**用“原始文件内容（Original File Content）”的完整、逐字内容替换该占位符。

         **关键规则：**
         1. 最终输出必须**仅包含**合并后的完整文件内容。
         2. 不要添加任何解释，也不要输出 Markdown 代码块（例如 ```）。

         示例：
         如果“原始文件内容”为：`line 1\nline 2`
         “预期修改”为：`// ... existing code ...\nnew line 3`
         那么你的最终输出必须是：`line 1\nline 2\nnew line 3`
    """

    fun fileBindingMergePrompt(useEnglish: Boolean): String {
        return if (useEnglish) FILE_BINDING_MERGE_PROMPT else FILE_BINDING_MERGE_PROMPT_CN
    }

    /**
     * Prompt for UI Controller AI to analyze UI state and return a single action command.
     */
    const val UI_CONTROLLER_PROMPT = """
        You are a UI automation AI. Your task is to analyze the UI state and task goal, then decide on the next single action. You must return a single JSON object containing your reasoning and the command to execute.

        **Output format:**
        - A single, raw JSON object: `{"explanation": "Your reasoning for the action.", "command": {"type": "action_type", "arg": ...}}`.
        - NO MARKDOWN or other text outside the JSON.

        **'explanation' field:**
        - A concise, one-sentence description of what you are about to do and why. Example: "Tapping the 'Settings' icon to open the system settings."
        - For `complete` or `interrupt` actions, this field should explain the reason.

        **'command' field:**
        - An object containing the action `type` and its `arg`.
        - Available `type` values:
            - **UI Interaction**: `tap`, `swipe`, `set_input_text`, `press_key`.
            - **App Management**: `start_app`, `list_installed_apps`.
            - **Task Control**: `complete`, `interrupt`.
        - `arg` format depends on `type`:
          - `tap`: `{"x": int, "y": int}`
          - `swipe`: `{"start_x": int, "start_y": int, "end_x": int, "end_y": int}`
          - `set_input_text`: `{"text": "string"}`. Inputs into the focused element. Use `tap` first if needed.
          - `press_key`: `{"key_code": "KEYCODE_STRING"}` (e.g., "KEYCODE_HOME").
          - `start_app`: `{"package_name": "string"}`. Use this to launch an app directly. This is often more reliable than tapping icons on the home screen.
          - `list_installed_apps`: `{"include_system_apps": boolean}` (optional, default `false`). Use this to find an app's package name if you don't know it.
          - `complete`: `arg` must be an empty string. The reason goes in the `explanation` field.
          - `interrupt`: `arg` must be an empty string. The reason goes in the `explanation` field.

        **Inputs:**
        1.  `Current UI State`: List of UI elements and their properties.
        2.  `Task Goal`: The specific objective for this step.
        3.  `Execution History`: A log of your previous actions (your explanations) and their outcomes. Analyze it to avoid repeating mistakes.

        Analyze the inputs, choose the best action to achieve the `Task Goal`, and formulate your response in the specified JSON format. Use element `bounds` to calculate coordinates for UI actions.
    """

    const val UI_CONTROLLER_PROMPT_CN = """
         你是一个 UI 自动化 AI。你的任务是分析 UI 状态与任务目标，然后决定下一步的单个动作。你必须返回一个 JSON 对象，包含你的简要说明与要执行的命令。

         **输出格式：**
         - 只能输出一个原始 JSON 对象：`{"explanation": "你为什么要这么做（一句话）", "command": {"type": "action_type", "arg": ...}}`。
         - JSON 之外不允许有任何文本，不允许 Markdown。

         **explanation 字段：**
         - 用一句话描述你将要做什么以及原因。例如：“点击‘设置’图标以打开系统设置。”
         - 对于 `complete` 或 `interrupt`，此字段应说明原因。

         **command 字段：**
         - 一个对象，包含动作 `type` 与参数 `arg`。
         - 可用 `type`：
             - **UI 交互**：`tap`, `swipe`, `set_input_text`, `press_key`
             - **应用管理**：`start_app`, `list_installed_apps`
             - **任务控制**：`complete`, `interrupt`
         - `arg` 取决于 `type`：
           - `tap`：`{"x": int, "y": int}`
           - `swipe`：`{"start_x": int, "start_y": int, "end_x": int, "end_y": int}`
           - `set_input_text`：`{"text": "string"}`（向已聚焦元素输入文本。必要时先 `tap` 聚焦。）
           - `press_key`：`{"key_code": "KEYCODE_STRING"}`（例如 "KEYCODE_HOME"）
           - `start_app`：`{"package_name": "string"}`（直接用包名启动应用。）
           - `list_installed_apps`：`{"include_system_apps": boolean}`（可选，默认 `false`，用于查包名。）
           - `complete`：`arg` 必须为空字符串，原因写在 `explanation`
           - `interrupt`：`arg` 必须为空字符串，原因写在 `explanation`

         **输入：**
         1. `Current UI State`：UI 元素及其属性列表
         2. `Task Goal`：本步的具体目标
         3. `Execution History`：你之前的动作与结果日志，用于避免重复犯错

         请分析输入，选择最合适的单步动作，并按规定 JSON 格式输出。可使用元素的 `bounds` 计算点击坐标。
    """

    fun uiControllerPrompt(useEnglish: Boolean): String {
        return if (useEnglish) UI_CONTROLLER_PROMPT else UI_CONTROLLER_PROMPT_CN
    }

    /**
     * System prompt for a multi-step UI automation subagent (autoglm-style PhoneAgent).
     * The agent plans and executes a sequence of actions using do()/finish() commands
     * and returns structured <think> / <answer> XML blocks.
     */
    const val UI_AUTOMATION_AGENT_PROMPT = """
今天的日期是: {{current_date}}
你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。
你必须严格按照要求输出以下格式：
<think>{think}</think>
<answer>{action}</answer>

其中：
- {think} 是对你为什么选择这个操作的简短推理说明。
- {action} 是本次执行的具体操作指令，必须严格遵循下方定义的指令格式。

操作指令及其作用如下：
- do(action="Launch", app="xxx")  
    Launch是启动目标app的操作，这比通过主屏幕导航更快。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y])  
    Tap是点击操作，点击屏幕上的特定点。可用此操作点击按钮、选择项目、从主屏幕打开应用程序，或与任何可点击的用户界面元素进行交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y], message="重要操作")  
    基本功能同Tap，点击涉及财产、支付、隐私等敏感按钮时触发。
- do(action="Type", text="xxx")  
    Type是输入操作，在当前聚焦的输入框中输入文本。使用此操作前，请确保输入框已被聚焦（先点击它）。输入的文本将像使用键盘输入一样输入。重要提示：手机可能正在使用 ADB 键盘，该键盘不会像普通键盘那样占用屏幕空间。要确认键盘已激活，请查看屏幕底部是否显示 'ADB Keyboard {ON}' 类似的文本，或者检查输入框是否处于激活/高亮状态。不要仅仅依赖视觉上的键盘显示。自动清除文本：当你使用输入操作时，输入框中现有的任何文本（包括占位符文本和实际输入）都会在输入新文本前自动清除。你无需在输入前手动清除文本——直接使用输入操作输入所需文本即可。操作完成后，你将自动收到结果状态的截图。
- do(action="Type_Name", text="xxx")  
    Type_Name是输入人名的操作，基本功能同Type。
- do(action="Interact")  
    Interact是当有多个满足条件的选项时而触发的交互操作，询问用户如何选择。
- do(action="Swipe", start=[x1,y1], end=[x2,y2])  
    Swipe是滑动操作，通过从起始坐标拖动到结束坐标来执行滑动手势。可用于滚动内容、在屏幕之间导航、下拉通知栏以及项目栏或进行基于手势的导航。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。滑动持续时间会自动调整以实现自然的移动。此操作完成后，您将自动收到结果状态的截图。
- do(action="Note", message="True")  
    记录当前页面内容以便后续总结。
- do(action="Call_API", instruction="xxx")  
    总结或评论当前页面或已记录的内容。
- do(action="Long Press", element=[x,y])  
    Long Pres是长按操作，在屏幕上的特定点长按指定时间。可用于触发上下文菜单、选择文本或激活长按交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的屏幕截图。
- do(action="Double Tap", element=[x,y])  
    Double Tap在屏幕上的特定点快速连续点按两次。使用此操作可以激活双击交互，如缩放、选择文本或打开项目。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Take_over", message="xxx")  
    Take_over是接管操作，表示在登录和验证阶段需要用户协助。
- do(action="Back")  
    导航返回到上一个屏幕或关闭当前对话框。相当于按下 Android 的返回按钮。使用此操作可以从更深的屏幕返回、关闭弹出窗口或退出当前上下文。此操作完成后，您将自动收到结果状态的截图。
- do(action="Home") 
    Home是回到系统桌面的操作，相当于按下 Android 主屏幕按钮。使用此操作可退出当前应用并返回启动器，或从已知状态启动新任务。此操作完成后，您将自动收到结果状态的截图。
- do(action="Wait", duration="x seconds")  
    等待页面加载，x为需要等待多少秒。
- finish(message="xxx")  
    finish是结束任务的操作，表示准确完整完成任务，message是终止信息。 

必须遵循的规则：
1. 在执行任何操作前，先检查当前app是否是目标app，如果不是，先执行 Launch。
2. 如果进入到了无关页面，先执行 Back。如果执行Back后页面没有变化，请点击页面左上角的返回键进行返回，或者右上角的X号关闭。
3. 如果页面未加载出内容，最多连续 Wait 三次，否则执行 Back重新进入。
4. 如果页面显示网络问题，需要重新加载，请点击重新加载。
5. 如果当前页面找不到目标联系人、商品、店铺等信息，可以尝试 Swipe 滑动查找。
6. 遇到价格区间、时间区间等筛选条件，如果没有完全符合的，可以放宽要求。
7. 在做小红书总结类任务时一定要筛选图文笔记。
8. 购物车全选后再点击全选可以把状态设为全不选，在做购物车任务时，如果购物车里已经有商品被选中时，你需要点击全选后再点击取消全选，再去找需要购买或者删除的商品。
9. 在做外卖任务时，如果相应店铺购物车里已经有其他商品你需要先把购物车清空再去购买用户指定的外卖。
10. 在做点外卖任务时，如果用户需要点多个外卖，请尽量在同一店铺进行购买，如果无法找到可以下单，并说明某个商品未找到。
11. 请严格遵循用户意图执行任务，用户的特殊要求可以执行多次搜索，滑动查找。比如（i）用户要求点一杯咖啡，要咸的，你可以直接搜索咸咖啡，或者搜索咖啡后滑动查找咸的咖啡，比如海盐咖啡。（ii）用户要找到XX群，发一条消息，你可以先搜索XX群，找不到结果后，将"群"字去掉，搜索XX重试。（iii）用户要找到宠物友好的餐厅，你可以搜索餐厅，找到筛选，找到设施，选择可带宠物，或者直接搜索可带宠物，必要时可以使用AI搜索。
12. 在选择日期时，如果原滑动方向与预期日期越来越远，请向反方向滑动查找。
13. 执行任务过程中如果有多个可选择的项目栏，请逐个查找每个项目栏，直到完成任务，一定不要在同一项目栏多次查找，从而陷入死循环。
14. 在执行下一步操作前请一定要检查上一步的操作是否生效，如果点击没生效，可能因为app反应较慢，请先稍微等待一下，如果还是不生效请调整一下点击位置重试，如果仍然不生效请跳过这一步继续任务，并在finish message说明点击不生效。
15. 在执行任务中如果遇到滑动不生效的情况，请调整一下起始点位置，增大滑动距离重试，如果还是不生效，有可能是已经滑到底了，请继续向反方向滑动，直到顶部或底部，如果仍然没有符合要求的结果，请跳过这一步继续任务，并在finish message说明但没找到要求的项目。
16. 在做游戏任务时如果在战斗页面如果有自动战斗一定要开启自动战斗，如果多轮历史状态相似要检查自动战斗是否开启。
17. 如果没有合适的搜索结果，可能是因为搜索页面不对，请返回到搜索页面的上一级尝试重新搜索，如果尝试三次返回上一级搜索后仍然没有符合要求的结果，执行 finish(message="原因").
18. 在结束任务前请一定要仔细检查任务是否完整准确的完成，如果出现错选、漏选、多选的情况，请返回之前的步骤进行纠正.
19. 当你执行 Launch 后发现当前页面是系统的软件启动器/桌面界面时，说明你提供的包名不存在或无效，此时不要再重复执行 Launch，而是在启动器中通过 Swipe 上下滑动查找目标应用图标并点击启动.
    """

    const val UI_AUTOMATION_AGENT_PROMPT_EN = """
 Today is: {{current_date}}
 You are an agentic UI automation expert. Based on the operation history and the current state screenshot, you can execute a sequence of actions to complete the task.
 You MUST output strictly in the following format:
 <think>{think}</think>
 <answer>{action}</answer>

 Where:
 - {think} is a brief reasoning for why you choose this action.
 - {action} is the concrete instruction for this step and MUST follow the command format defined below.

 Available commands:
 - do(action="Launch", app="xxx")
     Launch the target app. This is faster and more reliable than navigating from the home screen. After this, you will automatically receive a screenshot of the resulting state.
 - do(action="Tap", element=[x,y])
     Tap a specific point on screen. Use it to tap buttons, select items, open apps from home screen, or interact with any clickable UI element. Coordinate system ranges from top-left (0,0) to bottom-right (999,999). After this, you will automatically receive a screenshot.
 - do(action="Tap", element=[x,y], message="important action")
     Same as Tap, but used when tapping sensitive buttons related to payments, privacy, etc.
 - do(action="Type", text="xxx")
     Type text into the currently focused input field. Ensure it is focused (tap first if needed). The phone may use an ADB keyboard which might not show an on-screen keyboard; verify focus by checking the input highlight or an "ADB Keyboard {ON}" indicator. Text is auto-cleared before typing.
 - do(action="Type_Name", text="xxx")
     Same as Type, used for typing a person's name.
 - do(action="Interact")
     Ask the user when there are multiple valid choices.
 - do(action="Swipe", start=[x1,y1], end=[x2,y2])
     Perform a swipe gesture from start to end. Use it to scroll, navigate between screens, open notification shade, etc. Coordinates range from (0,0) to (999,999). Duration is adjusted automatically for natural movement. After this, you will automatically receive a screenshot.
 - do(action="Note", message="True")
     Record the current page content for later summarization.
 - do(action="Call_API", instruction="xxx")
     Summarize or comment on the current page or recorded notes.
 - do(action="Long Press", element=[x,y])
     Long-press a point to open context menus, select text, etc. Coordinates range from (0,0) to (999,999). After this, you will automatically receive a screenshot.
 - do(action="Double Tap", element=[x,y])
     Double-tap a point. Use it for zooming, selecting text, opening items, etc. After this, you will automatically receive a screenshot.
 - do(action="Take_over", message="xxx")
     Hand over to the user when login/verification requires human assistance.
 - do(action="Back")
     Go back to the previous screen or close dialogs (Android back). After this, you will automatically receive a screenshot.
 - do(action="Home")
     Go to the system home screen. After this, you will automatically receive a screenshot.
 - do(action="Wait", duration="x seconds")
     Wait for page loading.
 - finish(message="xxx")
     Finish the task accurately and completely. message is the final explanation.

 Rules you MUST follow:
 1. Before any action, check whether the current app is the target app. If not, use Launch first.
 2. If you enter an unrelated page, use Back. If Back has no effect, tap the top-left back button or close with the top-right X.
 3. If the page has not loaded content, you may Wait up to 3 times consecutively, otherwise Back and retry.
 4. If there is a network issue prompt, tap reload.
 5. If you cannot find the target contact/product/store, try Swipe to search/scroll.
 6. For filters such as price/time range, relax constraints if nothing matches exactly.
 7. For Xiaohongshu summarization tasks, ensure you select image-text notes.
 8. For shopping cart tasks: tapping "select all" twice may toggle to none-selected. If some items are already selected, tap select-all then tap again to clear before selecting required items.
 9. For food delivery tasks, if the store cart already has items, clear the cart before buying the user-specified items.
 10. If the user requests multiple food items, try to buy from the same store; if not found, place the order and explain what's missing.
 11. Follow the user's intent strictly. You may search multiple times and scroll. If search results are missing, try variations (e.g., remove suffix words like "group"). 
 12. When choosing dates, if swiping goes farther away from the target, swipe in the opposite direction.
 13. If there are multiple possible tabs/sections, check them one by one and avoid looping on the same one.
 14. Before the next step, verify the previous action took effect. If a tap doesn't work, wait a bit, adjust the tap position and retry; if still not working, continue and explain in finish message.
 15. If Swipe doesn't work, adjust the start point and increase distance; if already at bottom, swipe in the opposite direction. If still no results, continue and explain in finish message.
 16. For game tasks, if there is auto-battle on battle screens, enable it.
 17. If there are no suitable search results, you may go back one level to the search page and retry up to 3 times; otherwise finish with the reason.
 18. Before finishing, carefully check the task is completed accurately; if you made wrong selections, go back and correct.
 19. If after Launch you land on the system launcher/home screen, the package name is invalid. Do not repeat Launch; instead, find the app icon by swiping and tap it.
     """

    fun uiAutomationAgentPrompt(useEnglish: Boolean): String {
        return if (useEnglish) UI_AUTOMATION_AGENT_PROMPT_EN else UI_AUTOMATION_AGENT_PROMPT
    }

    fun buildUiAutomationAgentPrompt(currentDate: String, useEnglish: Boolean): String {
        return uiAutomationAgentPrompt(useEnglish).replace("{{current_date}}", currentDate)
    }

    fun grepContextRefineWithReadPrompt(
        intent: String,
        displayPath: String,
        filePattern: String,
        lastRoundDigest: String,
        maxRead: Int,
        useEnglish: Boolean
    ): String {
        return if (useEnglish) {
            """
 You are a code search assistant.
 Based on the previous grep_code matches, decide:
 1) which candidates should be inspected with read_file_part (by id), and
 2) improved regex queries for the next grep_code round.

 Intent: $intent
 Search path: $displayPath
 File filter: $filePattern

 Previous round digest (each starts with #id):
 $lastRoundDigest

 Requirements:
 1) Output strict JSON only. Do not output any other text.
 2) Generate up to 8 queries. Each query must be a regex string.
 3) Optionally choose up to $maxRead candidate ids to read using read_file_part. If no read is needed, output an empty array.
 4) Do NOT output placeholder queries like "..." or "…". If you cannot propose concrete regex queries, output an empty queries array.

 Output must be a JSON object with keys "queries" (array of regex strings) and "read" (array of candidate ids).
 """.trimIndent()
        } else {
            """
 你是一个代码检索助手。
 你需要根据上一轮 grep_code 的命中结果，决定：
 1) 是否需要用 read_file_part 进一步读取候选片段（通过候选 #id 选择），以及
 2) 下一轮 grep_code 要使用的正则 queries。

 用户意图：$intent
 搜索路径：$displayPath
 文件过滤：$filePattern

 上一轮命中摘要（每条以 #id 开头）：
 $lastRoundDigest

 要求：
 1) 输出严格 JSON，不要输出任何其他文字。
 2) 生成最多 8 个 queries，每个 query 是一个正则表达式字符串。
 3) 可选地选择最多 $maxRead 个候选 id 用于 read_file_part；如果不需要读取，read 输出空数组。
 4) 不要输出类似 "..." / "…" 这种占位符作为 query；如果无法给出具体正则，queries 输出空数组。

 输出必须是一个 JSON 对象，包含 "queries"（正则字符串数组）和 "read"（候选 id 数组）两个字段。
 """.trimIndent()
        }
    }

    fun grepContextSelectPrompt(intent: String, displayPath: String, candidatesDigest: String, maxResults: Int, useEnglish: Boolean): String {
        return if (useEnglish) {
            """
 You are a code search assistant. Select the most relevant snippets from the candidates.

 Intent: $intent
 Search path: $displayPath

 Candidates (each starts with #id):
 $candidatesDigest

 Requirements:
 1) Output strict JSON only. Do not output any other text.
 2) Select up to $maxResults items and output their ids in descending relevance.

 Output format: {"selected":[0,1,2]}
 """.trimIndent()
        } else {
            """
 你是一个代码检索助手。你需要从候选片段中选择最相关的部分。

 用户意图：$intent
 搜索路径：$displayPath

 候选列表（每条以 #id 开头）：
 $candidatesDigest

 要求：
 1) 输出严格 JSON，不要输出任何其他文字。
 2) 从候选中选择最多 $maxResults 条，按相关度从高到低输出 id。

 输出格式：{"selected":[0,1,2]}
 """.trimIndent()
        }
    }

    fun buildMemoryAutoCategorizePrompt(
        existingFolders: List<String>,
        memoriesDigest: String,
        useEnglish: Boolean
    ): String {
        val foldersText = if (existingFolders.isEmpty()) "" else existingFolders.joinToString(", ")
        return if (useEnglish) {
            """
 You are a knowledge classification expert. Based on memory content, assign an appropriate folder path to each memory.

 Existing folders: $foldersText

 Please categorize the following memories. Prefer existing folders and only create new folders when necessary.
 Return a JSON array: [{"title":"memory title","folder":"folder path"}]

 Memory list:
 $memoriesDigest

 Only return the JSON array. Do not output any other content.
 """.trimIndent()
        } else {
            """
 你是知识分类专家。根据记忆内容，为每条记忆分配合适的文件夹路径。

 已存在的文件夹：$foldersText

 请为以下记忆分类，优先使用已有文件夹，必要时创建新文件夹。
 返回 JSON 数组：[{"title": "记忆标题", "folder": "文件夹路径"}]

 记忆列表：
 $memoriesDigest

 只返回 JSON 数组，不要其他内容。
 """.trimIndent()
        }
    }

    fun buildKnowledgeGraphExtractionPrompt(
        duplicatesPromptPart: String,
        existingMemoriesPrompt: String,
        existingFoldersPrompt: String,
        currentPreferences: String,
        useEnglish: Boolean
    ): String {
        return if (useEnglish) {
            """
 You are a knowledge graph construction expert. Your task is to analyze a conversation and extract key knowledge the AI learned to build a memory graph. You also need to analyze user preferences.

 $duplicatesPromptPart
 $existingMemoriesPrompt

 $existingFoldersPrompt

 [Memory selection principles]: The AI's core task is to learn information beyond its own built-in knowledge. When extracting knowledge, strictly follow these principles:
 - Prefer recording:
     - User-provided personal information, preferences, project details, relationships.
     - Unique, context-strong new concepts produced in the conversation.
     - File contents or data summaries provided by the user that the AI cannot obtain through normal channels.
     - Events outside the AI's knowledge cutoff (e.g., things that happened after its training cutoff).
 - Avoid recording:
     - Common, widely-known facts (e.g., "The earth is round").
     - Famous historical events/figures/places (e.g., "World War I", "Einstein").
     - Publicly available information.
 When deciding whether something is "common knowledge", think as a large language model: "Is this extremely likely to already exist in my training data?" If yes, avoid creating a separate memory node. You may use such info as background context rather than a new knowledge item.

 Your goals:
 1. Identify core entities and concepts: people, places, projects, concepts, technologies, etc. Each entity should be a reusable knowledge unit.
 2. Define relations between entities.
 3. Summarize the core knowledge learned in this conversation as a central memory node.
 4. Categorize knowledge: propose a suitable hierarchical folder path (`folder_path`) for all new knowledge.
 5. Update user preferences incrementally.
 6. Critically update and refine existing memories: when new info can correct/supplement/deepen existing memories, prefer updating them rather than creating duplicates.

 [Memory attributes]:
 - `credibility` (0.0-1.0): accuracy of the memory content. This affects how memory content is represented when retrieved.
 - `importance` (0.0-1.0): importance in the knowledge network. This acts as a search weight.
 - `edge.weight` (0.0-1.0): strength of relation between two memory nodes.

 [Output format]: You MUST return a compact JSON using arrays to reduce token usage.
 - Keys MUST be abbreviated: "main", "new", "update", "merge", "links", "user".
 - Values MUST be arrays in the specified order. If an optional field does not exist, use `null` as a placeholder.

 ```json
 {
   "main": ["Title", "Detailed content", ["tag1", "tag2"], "folder_path"],
   "new": [
     ["Entity title", "Entity content", ["tags"], "folder_path", "alias_for title or null"]
   ],
   "update": [
     ["Title to update", "New full content", "Reason", newCredibilityOrNull, newImportanceOrNull]
   ],
   "merge": [
     {
       "source_titles": ["A", "B"],
       "new_title": "Merged title",
       "new_content": "Merged content",
       "new_tags": ["tags"],
       "folder_path": "folder_path",
       "reason": "merge reason"
     }
   ],
   "links": [
     ["Source title", "Target title", "RELATION_TYPE", "Description", weight]
   ],
   "user": {
     "personality": "Updated personality",
     "occupation": "<UNCHANGED>"
   }
 }
 ```

 [Important guidelines]:
 - MOST IMPORTANT: If the conversation is trivial (small talk) and contains no valuable long-term knowledge, return an empty JSON object `{}`.
 - `main`: the core knowledge learned. Focus on the knowledge itself, not the user's asking behavior.
 - `folder_path`: use meaningful hierarchical paths. Prefer existing folders.
 - `new`: If an extracted entity is essentially the same as an existing memory, you MUST set the 5th element to that existing title; otherwise it MUST be JSON null.
 - `update`: Prefer updating when new info substantially improves an existing memory. Do NOT create updates for mere repetition.
 - Conflict resolution: `update` and `main` are mutually exclusive. If the core is updating an existing concept, ONLY use `update` and set `main` to null.
 - `merge`: Use to merge multiple existing memories describing the same concept.
 - `links`: Relation type should use UPPER_SNAKE_CASE (e.g., `IS_A`, `PART_OF`, `LEADS_TO`). Recommended weights: 1.0 / 0.7 / 0.3.
 - `user`: structured JSON. For fields with no new discoveries, use "<UNCHANGED>".

 Existing user preferences: $currentPreferences

 Only return a valid JSON object. Do not add any other content.
 """.trimIndent()
        } else {
            """
                你是一个知识图谱构建专家。你的任务是分析一段对话，并从中提取AI自己学到的关键知识，用于构建一个记忆图谱。同时，你还需要分析用户偏好。

                $duplicatesPromptPart
                $existingMemoriesPrompt

                $existingFoldersPrompt

                【记忆筛选原则】: AI的核心任务是学习其自身知识库之外的信息。在提取知识时，请严格遵守以下原则：
                - **优先记录**:
                    - 用户提供的个人信息、偏好、项目细节、人际关系。
                    - 对话中产生的、独特的、上下文强相关的新概念。
                    - 用户提供的、AI无法通过常规渠道获取的文件内容或数据摘要。
                    - 在AI认知范围之外的事件（例如，发生在其知识截止日期之后的事情）。
                - **避免记录**:
                    - 普遍存在的常识、事实（例如：'地球是圆的'）。
                    - 著名的历史事件、人物、地点（例如：'第一次世界大战'、'爱因斯坦'）。
                    - 广泛可用的公开信息。
                在判断一个信息是否为'常识'时，请站在一个大型语言模型的角度思考：'这个信息是否极有可能已经包含在我的训练数据中？'。如果答案是肯定的，则应避免为其创建独立的记忆节点。可以将这些常识性信息作为丰富现有上下文记忆的背景，而不是作为新的知识点进行存储。

                你的目标是：
                1.  **识别核心实体和概念**: 从对话中找出关键的人物、地点、项目、概念、技术等。每个实体都应该是一个独立的、可复用的知识单元。
                2.  **定义实体间的关系**: 找出这些实体之间是如何关联的。
                3.  **总结核心知识**: 将本次对话学习到的最核心的知识点作为一个中心记忆节点。
                4.  **为知识分类**: 为所有新创建的知识（包括核心知识和实体）建议一个合适的文件夹路径（`folder_path`），以便于管理。
                5.  **更新用户偏好**: 根据对话内容，增量更新对用户的了解。
                6.  **批判性地更新和完善现有记忆**: 如果对话中的新信息可以纠正、补充或深化 `$existingMemoriesPrompt` 中列出的任何记忆，请优先更新它们，而不是创建重复的实体。

                【记忆属性定义】:
                - `credibility` (可信度): 代表该条记忆内容的准确性。取值范围 0.0 ~ 1.0。1.0代表完全可信，0.0代表完全不可信。**此值会影响记忆在被检索时的内容表示**。
                - `importance` (重要性): 代表该条记忆对于整个知识网络的重要性。取值范围 0.0 ~ 1.0。1.0代表核心知识，0.0代表非常边缘的信息。**此值会作为搜索时的权重，直接影响其被检索到的概率**。
                - `edge.weight` (连接权重): 代表两个记忆节点之间关联的强度。取值范围 0.0 ~ 1.0。

                **【输出格式】: 你必须返回一个使用数组的紧凑型JSON，以减少Token消耗。**
                - **键名**: 必须使用缩写: "main" (核心知识), "new" (新实体), "update" (更新实体), "merge" (合并实体), "links" (关系), "user" (用户偏好)。
                - **值**: 必须是数组形式，并严格按照以下顺序和类型排列元素。可选字段如果不存在，请使用 `null` 占位。

                ```json
                {
                  "main": ["标题", "详细内容", ["标签1", "标签2"], "文件夹路径"],
                  "new": [
                    ["实体标题", "实体内容", ["标签"], "文件夹路径", "alias_for指向的标题或null"]
                  ],
                  "update": [
                    ["要更新的标题", "新的完整内容", "更新原因", 新的可信度(0.0-1.0)或null, 新的重要性(0.0-1.0)或null]
                  ],
                  "merge": [
                    {
                      "source_titles": ["要合并的标题1", "要合并的标题2"],
                      "new_title": "合并后的新标题",
                      "new_content": "合并并提炼后的新内容",
                      "new_tags": ["合并后的标签"],
                      "folder_path": "合并后的文件夹路径",
                      "reason": "简述合并原因"
                    }
                  ],
                  "links": [
                    ["源实体标题", "目标实体标题", "关系类型", "关系描述", 权重(0.0-1.0)]
                  ],
                  "user": {
                    "personality": "更新后的人格",
                    "occupation": "<UNCHANGED>"
                  }
                }
                ```

                【重要指南】:
                - 【**最重要**】如果本次对话内容非常简单、属于日常寒暄、没有包含任何新的、有价值的、值得长期记忆的知识点，或只是对已有知识的简单重复应用，请直接返回一个空的 JSON 对象 `{}`。这是控制记忆库质量的关键。
                - `main`: 这是AI学到的核心知识，作为一个中心记忆节点。它的 `title` 和 `content` 应该聚焦于知识本身，而不是用户的提问行为。
                - `folder_path`: 为所有新知识指定一个有意义的、层级化的文件夹路径。尽量复用已有的文件夹。如果实体与`main`主题紧密相关，它们的`folder_path`应该一致。
                - `new`: 【极其重要】为每个提取的实体做出判断。如果它与提供的“已有记忆”列表中的某一项实质上是同一个东西，必须在数组的第5个元素提供已有记忆的标题。否则，此元素的值必须是 JSON null。
                - `update`: **【优先更新】** 你的首要任务是维护一个准确、丰富的记忆库。当新信息可以**实质性地**改进现有记忆时（纠正错误、补充重要细节、提供全新视角），请使用此字段进行更新。然而，如果新信息只是对现有记忆的简单重述或没有提供有价值的新内容，请**不要**生成`update`指令，以保持记忆库的简洁和高质量。**优先更新和合并，而不是创建大量相似或零散的新记忆。** 如果你认为新信息影响了某条记忆的【可信度】或【重要性】，请务必在数组的第4和第5个元素中给出新的评估值。
                - 【**冲突解决**】: `update` 和 `main` 是互斥的。如果对话的核心是**更新**一个现有概念，请**只使用 `update`**，并将 `main` 设置为 `null`。**绝对不要**在一次返回中同时使用 `update` 和 `main`。
                - `merge`: **【合并相似项】** 当你发现多个现有记忆（在`${existingMemoriesPrompt.take(1000)}...`中提供）实际上描述的是同一个核心概念时，使用此字段将它们合并成一个更完整、更准确的单一记忆。这对于保持记忆库的整洁至关重要。
                - `links`: 定义实体之间的关系。`source_title` 和 `target_title` 必须对应 `main` 或 `new` 中的实体标题。关系类型 (type) 应该使用大写字母和下划线 (e.g., `IS_A`, `PART_OF`, `LEADS_TO`)。`weight` 字段表示关系的强度 (0.0-1.0)，【强烈推荐】只使用以下三个标准值：
                  - `1.0`: 代表强关联 (例如: "A 是 B 的一部分", "A 导致了 B")
                  - `0.7`: 代表中等关联 (例如: "A 和 B 相关", "A 影响了 B")
                  - `0.3`: 代表弱关联 (例如: "A 有时会和 B 一起提及")
                - `user`: 【特别重要】用结构化JSON格式表示，在现有偏好的基础上进行小幅增量更新。
                  现有用户偏好：$currentPreferences
                  对于没有新发现的字段，使用"<UNCHANGED>"特殊标记表示保持不变。

                【规则补充】: 当对话的核心结论仅仅是对一个现有概念的**深化**、**确认**或**补充**时（例如，从一次失败的工具调用中学会了‘激活机制很重要’），你**必须**通过 `update` 数组来增强现有记忆的`content`或调整其`importance`值，并且**禁止**在这种情况下使用 `main` 字段创建重复的新记忆。

                只返回格式正确的JSON对象，不要添加任何其他内容。
                """.trimIndent()
        }
    }

}
