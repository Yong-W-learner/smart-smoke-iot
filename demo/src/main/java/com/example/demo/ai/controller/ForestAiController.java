package com.example.demo.ai.controller;

import com.example.demo.ai.service.ForestAiChatService;
import com.example.demo.ai.support.AiAuthService;
import com.example.demo.ai.support.AiAuthService.Principal;
import com.example.demo.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 森林防火 AI 助手接口。统一走现有 Result 约定；鉴权沿用 mock-token 体系。
 * 健康检查无需登录但只返回布尔状态，不暴露内部路径与堆栈。
 */
@RestController
@RequestMapping("/api/forest/ai")
public class ForestAiController {

    private static final Logger log = LoggerFactory.getLogger(ForestAiController.class);

    private final ForestAiChatService chatService;
    private final AiAuthService authService;

    public ForestAiController(ForestAiChatService chatService, AiAuthService authService) {
        this.chatService = chatService;
        this.authService = authService;
    }

    /** AI 健康检查：AI/Ollama/模型/Qdrant/知识库状态与降级说明。 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(chatService.health());
    }

    /** 项目对话：{conversationId 可选, message}。 */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Principal principal = requireUser(request);
        String conversationId = body.get("conversationId") == null ? null : String.valueOf(body.get("conversationId"));
        String message = body.get("message") == null ? "" : String.valueOf(body.get("message"));
        return Result.ok(chatService.chat(principal, conversationId, message));
    }

    @GetMapping("/conversations")
    public Result<Object> conversations(HttpServletRequest request) {
        Principal principal = requireUser(request);
        return Result.ok(chatService.listConversations(principal));
    }

    @GetMapping("/conversations/{conversationId}")
    public Result<Map<String, Object>> conversation(@PathVariable String conversationId, HttpServletRequest request) {
        Principal principal = requireUser(request);
        return Result.ok(chatService.getConversation(principal, conversationId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Result<String> deleteConversation(@PathVariable String conversationId, HttpServletRequest request) {
        Principal principal = requireUser(request);
        chatService.deleteConversation(principal, conversationId);
        return Result.ok("会话已清除");
    }

    /** 知识库文档列表（登录可见）。 */
    @GetMapping("/knowledge/documents")
    public Result<Object> knowledgeDocuments(HttpServletRequest request) {
        Principal principal = requireUser(request);
        return Result.ok(chatService.knowledgeDocuments(principal));
    }

    /** 重建知识库：仅管理员。 */
    @PostMapping("/knowledge/reindex")
    public Result<String> reindex(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        Principal principal = requireUser(request);
        boolean force = body != null && Boolean.TRUE.equals(body.get("force"));
        return Result.ok(chatService.reindex(principal, force));
    }

    /** 导入知识文档：仅管理员，限 .md/.txt/.pdf 与大小。 */
    @PostMapping(value = "/knowledge/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Principal principal = requireUser(request);
        return Result.ok(chatService.uploadDocument(principal, file));
    }

    // ---------- 错误处理（不泄露堆栈/路径/连接串） ----------

    private Principal requireUser(HttpServletRequest request) {
        Principal principal = authService.current(request);
        if (principal == null) throw new ForestAiChatService.AiDeniedException(401, "请先登录护林员账号");
        return principal;
    }

    @ExceptionHandler(ForestAiChatService.AiDeniedException.class)
    public Result<Void> handleDenied(ForestAiChatService.AiDeniedException e) {
        return Result.error(e.code, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleBadRequest(IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.warn("AI 接口异常：{}", e.toString());
        return Result.fail("AI 服务暂时不可用，原有业务不受影响");
    }
}
