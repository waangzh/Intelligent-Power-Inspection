package com.powerinspection.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.api.AgentDtos;
import com.powerinspection.agent.domain.AgentCaseEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.domain.AgentEvidenceEntity;
import com.powerinspection.agent.domain.AgentHumanAnswerEntity;
import com.powerinspection.agent.domain.AgentHumanQuestionEntity;
import com.powerinspection.agent.domain.AgentRunEntity;
import com.powerinspection.agent.domain.AgentStepEntity;
import com.powerinspection.agent.persistence.AgentCaseRepository;
import com.powerinspection.agent.persistence.AgentEvidenceRepository;
import com.powerinspection.agent.persistence.AgentHumanAnswerRepository;
import com.powerinspection.agent.persistence.AgentHumanQuestionRepository;
import com.powerinspection.agent.persistence.AgentRunRepository;
import com.powerinspection.agent.persistence.AgentStepRepository;
import com.powerinspection.agent.planner.PlannerQuestion;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import com.powerinspection.user.UserEntity;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AgentHumanInputService {
  private static final Logger log = LoggerFactory.getLogger(AgentHumanInputService.class);
  private static final Set<AgentEnums.EvidenceSourceType> REFERENCEABLE_ATTACHMENTS = Set.of(
    AgentEnums.EvidenceSourceType.ALARM, AgentEnums.EvidenceSourceType.TASK_EVENT, AgentEnums.EvidenceSourceType.VISION_RESULT
  );
  private final AgentHumanQuestionRepository questionRepository;
  private final AgentHumanAnswerRepository answerRepository;
  private final AgentCaseRepository caseRepository;
  private final AgentRunRepository runRepository;
  private final AgentEvidenceRepository evidenceRepository;
  private final AgentStepRepository stepRepository;
  private final PermissionService permissionService;
  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;

  public AgentHumanInputService(AgentHumanQuestionRepository questionRepository, AgentHumanAnswerRepository answerRepository, AgentCaseRepository caseRepository, AgentRunRepository runRepository, AgentEvidenceRepository evidenceRepository, AgentStepRepository stepRepository, PermissionService permissionService, ObjectMapper objectMapper, SimpMessagingTemplate messagingTemplate) {
    this.questionRepository = questionRepository; this.answerRepository = answerRepository; this.caseRepository = caseRepository; this.runRepository = runRepository;
    this.evidenceRepository = evidenceRepository; this.stepRepository = stepRepository; this.permissionService = permissionService; this.objectMapper = objectMapper; this.messagingTemplate = messagingTemplate;
  }

  @Transactional
  public Question createQuestion(AgentCaseEntity agentCase, AgentRunEntity run, PlannerQuestion request) {
    Instant now = Instant.now();
    AgentHumanQuestionEntity item = new AgentHumanQuestionEntity();
    item.setId(Ids.next("agent_question")); item.setCaseId(agentCase.getId()); item.setRunId(run.getId()); item.setQuestionType(request.type());
    item.setPrompt(request.prompt()); item.setOptionsJson(json(request.options())); item.setStatus(AgentEnums.HumanQuestionStatus.OPEN); item.setCreatedAt(now);
    item = questionRepository.save(item);
    return new Question(item.getId(), item.getQuestionType(), item.getPrompt(), request.options());
  }

  @Transactional
  public Submission submit(String runId, AgentDtos.HumanInputRequest request, UserEntity user) {
    permissionService.require(user, Permission.AGENT_RUN);
    AgentRunEntity run = runRepository.findById(runId).orElseThrow(() -> ApiException.notFound("分析运行不存在"));
    AgentHumanQuestionEntity question = questionRepository.findByIdAndRunId(request.questionId(), runId).orElseThrow(() -> ApiException.badRequest("问题不属于指定 Run"));
    AgentCaseEntity agentCase = caseRepository.findById(run.getCaseId()).orElseThrow(() -> ApiException.notFound("处置案件不存在"));
    if (!agentCase.getId().equals(question.getCaseId())) throw ApiException.badRequest("问题不属于指定案件");
    AgentEnums.HumanInputMode mode = request.mode();
    if (mode == null) throw ApiException.badRequest("必须选择回答方式");
    String text = trim(request.text());
    List<String> attachments = request.attachmentIds() == null ? List.of() : request.attachmentIds().stream().map(this::trim).toList();
    validateInput(mode, text, attachments, run);

    AgentHumanAnswerEntity existing = answerRepository.findByQuestionId(question.getId()).orElse(null);
    if (existing != null) {
      if (same(existing, mode, text, attachments, user.getId())) return new Submission(new AgentDtos.HumanInputResponse(existing.getId(), question.getId(), existing.getMode(), false), null);
      throw ApiException.conflict("该问题已由其他回答处理");
    }
    if (run.getStatus() != AgentEnums.RunStatus.WAITING_HUMAN || question.getStatus() != AgentEnums.HumanQuestionStatus.OPEN) {
      throw ApiException.conflict("当前 Run 不处于等待人工回答状态");
    }

    Instant now = Instant.now();
    AgentHumanAnswerEntity answer = new AgentHumanAnswerEntity();
    answer.setId(Ids.next("agent_answer")); answer.setQuestionId(question.getId()); answer.setCaseId(run.getCaseId()); answer.setRunId(run.getId()); answer.setMode(mode);
    answer.setAnswerText(text); answer.setAttachmentIdsJson(json(attachments)); answer.setAnswerUserId(user.getId()); answer.setCreatedAt(now);
    try {
      answer = answerRepository.save(answer);
    } catch (DataIntegrityViolationException ex) {
      throw ApiException.conflict("该问题已被其他回答处理，请刷新后重试");
    }
    question.setStatus(mode == AgentEnums.HumanInputMode.CANCEL_RUN ? AgentEnums.HumanQuestionStatus.CANCELLED : AgentEnums.HumanQuestionStatus.ANSWERED);
    question.setAnsweredAt(now); question.setAnsweredById(user.getId()); questionRepository.save(question);

    if (mode == AgentEnums.HumanInputMode.CANCEL_RUN) {
      run.setStatus(AgentEnums.RunStatus.CANCELLED); run.setPendingQuestionJson(null); run.setCompletedAt(now); run.setErrorCode("RUN_CANCELLED"); run.setErrorMessage("操作员取消了分析运行"); runRepository.save(run);
      agentCase.setStatus(AgentEnums.CaseStatus.OPEN); agentCase.setUpdatedAt(now); caseRepository.save(agentCase);
      recordStep(run, AgentEnums.StepType.RUN_FAILED, "操作员取消了分析运行", Map.of("questionId", question.getId(), "answerUserId", user.getId()));
      return new Submission(new AgentDtos.HumanInputResponse(answer.getId(), question.getId(), mode, false), null);
    }

    saveEvidence(agentCase, run, question, answer, attachments);
    run.setPendingQuestionJson(null); run.setStatus(AgentEnums.RunStatus.RUNNING); run.setErrorCode(null); run.setErrorMessage(null); runRepository.save(run);
    agentCase.setStatus(AgentEnums.CaseStatus.ANALYZING); agentCase.setUpdatedAt(now); caseRepository.save(agentCase);
    recordStep(run, AgentEnums.StepType.HUMAN_INPUT_RECEIVED, "已收到人工输入，继续执行 Agent", Map.of("questionId", question.getId(), "answerUserId", user.getId(), "mode", mode.name()));
    return new Submission(new AgentDtos.HumanInputResponse(answer.getId(), question.getId(), mode, true), run.getId());
  }

  public Map<String, Object> questionPayload(Question question) { return Map.of("questionId", question.id(), "type", question.type(), "prompt", question.prompt(), "options", question.options()); }

  private void validateInput(AgentEnums.HumanInputMode mode, String text, List<String> attachments, AgentRunEntity run) {
    if (attachments.size() > 10 || attachments.stream().anyMatch(String::isBlank) || attachments.stream().distinct().count() != attachments.size()) throw ApiException.badRequest("附件引用不合法");
    for (String id : attachments) {
      AgentEvidenceEntity evidence = evidenceRepository.findById(id).orElseThrow(() -> ApiException.badRequest("附件引用不存在"));
      if (!run.getId().equals(evidence.getRunId()) || !run.getCaseId().equals(evidence.getCaseId()) || !REFERENCEABLE_ATTACHMENTS.contains(evidence.getSourceType())) throw ApiException.forbidden("附件引用不属于当前 Run 或类型不允许");
    }
    if (mode == AgentEnums.HumanInputMode.ANSWER && text.isBlank() && attachments.isEmpty()) throw ApiException.badRequest("回答需要提供文本或附件引用");
    if (mode != AgentEnums.HumanInputMode.ANSWER && (!text.isBlank() || !attachments.isEmpty())) throw ApiException.badRequest("当前回答方式不接受补充文本或附件");
  }

  private void saveEvidence(AgentCaseEntity agentCase, AgentRunEntity run, AgentHumanQuestionEntity question, AgentHumanAnswerEntity answer, List<String> attachments) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("questionId", question.getId()); payload.put("answerUserId", answer.getAnswerUserId()); payload.put("answeredAt", answer.getCreatedAt().toString()); payload.put("mode", answer.getMode().name()); payload.put("text", answer.getAnswerText()); payload.put("attachmentIds", attachments); payload.put("untrusted", true);
    Instant now = Instant.now(); AgentEvidenceEntity item = new AgentEvidenceEntity();
    item.setId(Ids.next("agent_ev")); item.setCaseId(agentCase.getId()); item.setRunId(run.getId()); item.setSourceType(AgentEnums.EvidenceSourceType.OPERATOR_INPUT);
    item.setSourceId(question.getId()); item.setTitle("人工补充输入"); item.setSummary(answer.getMode() == AgentEnums.HumanInputMode.ANSWER ? abbreviate(answer.getAnswerText(), 1000) : "操作员要求基于现有证据继续。");
    item.setContentType("application/json"); item.setPayloadJson(json(payload)); item.setContentHash(sha256(item.getPayloadJson())); item.setCollectedAt(now); item.setCreatedAt(now); evidenceRepository.save(item);
    recordStep(run, AgentEnums.StepType.EVIDENCE_ADDED, item.getTitle(), Map.of("evidenceId", item.getId(), "sourceType", item.getSourceType().name()));
  }

  private void recordStep(AgentRunEntity run, AgentEnums.StepType type, String summary, Map<String, Object> detail) {
    AgentStepEntity step = new AgentStepEntity(); step.setId(Ids.next("agent_step")); step.setCaseId(run.getCaseId()); step.setRunId(run.getId()); step.setSequenceNo(stepRepository.findMaxSequenceNo(run.getId()) + 1); step.setType(type); step.setSummary(summary); step.setDetailJson(json(detail)); step.setCreatedAt(Instant.now()); step = stepRepository.save(step);
    try { messagingTemplate.convertAndSend("/topic/agent-cases/" + run.getCaseId(), new AgentDtos.AgentEvent(step.getId(), run.getCaseId(), run.getId(), type, step.getSequenceNo(), step.getSummary(), step.getCreatedAt())); } catch (Exception ex) { log.warn("Failed to send agent WebSocket event for case={}", run.getCaseId(), ex); }
  }
  private boolean same(AgentHumanAnswerEntity item, AgentEnums.HumanInputMode mode, String text, List<String> attachments, String userId) { return item.getMode() == mode && java.util.Objects.equals(item.getAnswerText(), text) && java.util.Objects.equals(item.getAttachmentIdsJson(), json(attachments)) && java.util.Objects.equals(item.getAnswerUserId(), userId); }
  private String trim(String value) { return value == null ? "" : value.trim(); }
  private String abbreviate(String value, int max) { return value == null ? "" : value.length() <= max ? value : value.substring(0, max); }
  private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException("Agent 人工输入审计序列化失败", ex); } }
  private String sha256(String value) { try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder output = new StringBuilder(); for (byte item : digest) output.append(String.format("%02x", item)); return output.toString(); } catch (Exception ex) { throw new IllegalStateException(ex); } }
  public record Question(String id, String type, String prompt, List<String> options) { }
  public record Submission(AgentDtos.HumanInputResponse response, String runIdToResume) { }
}
