package com.powerinspection.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_human_questions")
public class AgentHumanQuestionEntity {
  @Id private String id;
  @Column(name = "case_id", nullable = false) private String caseId;
  @Column(name = "run_id", nullable = false) private String runId;
  @Column(name = "question_type", nullable = false) private String questionType;
  @Column(nullable = false, columnDefinition = "LONGTEXT") private String prompt;
  @Column(name = "options_json", nullable = false, columnDefinition = "LONGTEXT") private String optionsJson;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private AgentEnums.HumanQuestionStatus status;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "answered_at") private Instant answeredAt;
  @Column(name = "answered_by_id") private String answeredById;
  public String getId() { return id; } public void setId(String value) { id = value; }
  public String getCaseId() { return caseId; } public void setCaseId(String value) { caseId = value; }
  public String getRunId() { return runId; } public void setRunId(String value) { runId = value; }
  public String getQuestionType() { return questionType; } public void setQuestionType(String value) { questionType = value; }
  public String getPrompt() { return prompt; } public void setPrompt(String value) { prompt = value; }
  public String getOptionsJson() { return optionsJson; } public void setOptionsJson(String value) { optionsJson = value; }
  public AgentEnums.HumanQuestionStatus getStatus() { return status; } public void setStatus(AgentEnums.HumanQuestionStatus value) { status = value; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
  public Instant getAnsweredAt() { return answeredAt; } public void setAnsweredAt(Instant value) { answeredAt = value; }
  public String getAnsweredById() { return answeredById; } public void setAnsweredById(String value) { answeredById = value; }
}
