package com.powerinspection.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_human_answers")
public class AgentHumanAnswerEntity {
  @Id private String id;
  @Column(name = "question_id", nullable = false, unique = true) private String questionId;
  @Column(name = "case_id", nullable = false) private String caseId;
  @Column(name = "run_id", nullable = false) private String runId;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private AgentEnums.HumanInputMode mode;
  @Column(name = "answer_text", columnDefinition = "LONGTEXT") private String answerText;
  @Column(name = "attachment_ids_json", nullable = false, columnDefinition = "LONGTEXT") private String attachmentIdsJson;
  @Column(name = "answer_user_id", nullable = false) private String answerUserId;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  public String getId() { return id; } public void setId(String value) { id = value; }
  public String getQuestionId() { return questionId; } public void setQuestionId(String value) { questionId = value; }
  public String getCaseId() { return caseId; } public void setCaseId(String value) { caseId = value; }
  public String getRunId() { return runId; } public void setRunId(String value) { runId = value; }
  public AgentEnums.HumanInputMode getMode() { return mode; } public void setMode(AgentEnums.HumanInputMode value) { mode = value; }
  public String getAnswerText() { return answerText; } public void setAnswerText(String value) { answerText = value; }
  public String getAttachmentIdsJson() { return attachmentIdsJson; } public void setAttachmentIdsJson(String value) { attachmentIdsJson = value; }
  public String getAnswerUserId() { return answerUserId; } public void setAnswerUserId(String value) { answerUserId = value; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
}
