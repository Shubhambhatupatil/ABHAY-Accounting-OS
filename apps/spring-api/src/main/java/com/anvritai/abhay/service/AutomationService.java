package com.anvritai.abhay.service;

import static com.anvritai.abhay.api.AutomationDtos.*;
import com.anvritai.abhay.domain.*;
import com.anvritai.abhay.domain.automation.*;
import com.anvritai.abhay.domain.banking.ReconciliationStatus;
import com.anvritai.abhay.domain.sales.InvoiceStatus;
import com.anvritai.abhay.repository.*;
import com.anvritai.abhay.repository.automation.*;
import com.anvritai.abhay.repository.banking.BankTransactionRepository;
import com.anvritai.abhay.repository.inventory.InventoryAlertRepository;
import com.anvritai.abhay.repository.sales.InvoiceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationService {
    private static final Set<String> RULE_TYPES=Set.of("GST_DUE","LOW_STOCK","RECEIVABLE_OVERDUE","BANK_UNRECONCILED","MONTH_END");
    private static final Set<String> SCHEDULES=Set.of("DAILY","WEEKLY","MONTHLY","FINANCIAL_YEAR","EVENT_TRIGGER");
    private static final Set<String> CHANNELS=Set.of("IN_APP","EMAIL","WHATSAPP","WEBHOOK");
    private final AutomationRuleRepository rules; private final AutomationRunRepository runs;
    private final AutomationLogRepository logs; private final AutomationNotificationRepository notifications;
    private final AutomationTemplateRepository templates; private final CompanyRepository companies; private final UserRepository users;
    private final CompanyAccessService access; private final AuditService audit; private final MemoryEventCaptureService memory;
    private final InventoryAlertRepository inventoryAlerts; private final BankTransactionRepository bankTransactions;
    private final InvoiceRepository invoices; private final ObjectMapper json;

    public AutomationService(AutomationRuleRepository rules,AutomationRunRepository runs,AutomationLogRepository logs,
            AutomationNotificationRepository notifications,AutomationTemplateRepository templates,CompanyRepository companies,
            UserRepository users,CompanyAccessService access,AuditService audit,MemoryEventCaptureService memory,
            InventoryAlertRepository inventoryAlerts,BankTransactionRepository bankTransactions,InvoiceRepository invoices,ObjectMapper json){
        this.rules=rules;this.runs=runs;this.logs=logs;this.notifications=notifications;this.templates=templates;
        this.companies=companies;this.users=users;this.access=access;this.audit=audit;this.memory=memory;
        this.inventoryAlerts=inventoryAlerts;this.bankTransactions=bankTransactions;this.invoices=invoices;this.json=json;
    }

    @Transactional(readOnly=true) public List<RuleResponse> rules(UUID userId,UUID companyId){ access.requireMembership(companyId,userId); return rules.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::response).toList(); }
    @Transactional(readOnly=true) public List<TemplateResponse> templates(UUID userId,UUID companyId){ access.requireMembership(companyId,userId); return templates.findAllByActiveTrueOrderByName().stream().map(t->new TemplateResponse(t.getId(),t.getCode(),t.getName(),t.getDescription(),t.getRuleType(),t.getDefaultScheduleType(),map(t.getDefaultParametersJson()))).toList(); }
    @Transactional public RuleResponse create(UUID userId,UUID companyId,CreateRuleRequest request){
        access.requireRole(companyId,userId,RoleCode.OWNER,RoleCode.ADMIN,RoleCode.ACCOUNTANT);
        String type=allowed(request.ruleType(),RULE_TYPES,"Unsupported automation rule type.");
        String schedule=allowed(request.scheduleType(),SCHEDULES,"Unsupported schedule type.");
        String channel=allowed(request.notificationChannel(),CHANNELS,"Unsupported notification channel.");
        Company company=company(companyId); User user=user(userId); AutomationRule rule=new AutomationRule();
        rule.setCompany(company);rule.setCreatedBy(user);rule.setName(request.name().trim());rule.setRuleType(type);
        rule.setScheduleType(schedule);rule.setNotificationChannel(channel);rule.setParametersJson(write(request.parameters()));
        rule.setActive(request.active()==null||request.active());rule.setNextRunAt(rule.isActive()?next(schedule,Instant.now()):null);rules.save(rule);
        audit.record(company,user,"AUTOMATION_RULE_CREATED","AUTOMATION_RULE",rule.getId(),Map.of("ruleType",type,"scheduleType",schedule));
        return response(rule);
    }
    @Transactional public RuleResponse update(UUID userId,UUID companyId,UUID ruleId,CreateRuleRequest request){
        access.requireRole(companyId,userId,RoleCode.OWNER,RoleCode.ADMIN,RoleCode.ACCOUNTANT);
        AutomationRule rule=rule(companyId,ruleId);rule.setName(request.name().trim());rule.setRuleType(allowed(request.ruleType(),RULE_TYPES,"Unsupported automation rule type."));
        rule.setScheduleType(allowed(request.scheduleType(),SCHEDULES,"Unsupported schedule type."));rule.setNotificationChannel(allowed(request.notificationChannel(),CHANNELS,"Unsupported notification channel."));
        rule.setParametersJson(write(request.parameters()));rule.setActive(request.active()==null||request.active());rule.setNextRunAt(rule.isActive()?next(rule.getScheduleType(),Instant.now()):null);
        User actor=user(userId);audit.record(rule.getCompany(),actor,"AUTOMATION_RULE_UPDATED","AUTOMATION_RULE",rule.getId(),Map.of("ruleType",rule.getRuleType(),"scheduleType",rule.getScheduleType()));return response(rule);
    }
    @Transactional public RunResponse run(UUID userId,UUID companyId,UUID ruleId){ access.requireRole(companyId,userId,RoleCode.OWNER,RoleCode.ADMIN,RoleCode.ACCOUNTANT); return execute(rule(companyId,ruleId),user(userId),"MANUAL"); }
    @Transactional public RunResponse execute(AutomationRule rule,User actor,String trigger){
        Instant now=Instant.now(); AutomationRun run=new AutomationRun();run.setCompany(rule.getCompany());run.setRule(rule);run.setTriggerType(trigger);run.setStatus("RUNNING");run.setStartedAt(now);run.setCorrelationId(correlation());runs.save(run);
        int count=matchCount(rule);String summary=summary(rule.getRuleType(),count);run.setMatchedCount(count);run.setSummary(summary);run.setStatus("SUCCESS");run.setCompletedAt(Instant.now());
        rule.setLastRunAt(now);rule.setNextRunAt(next(rule.getScheduleType(),now));
        AutomationLog log=new AutomationLog();log.setCompany(rule.getCompany());log.setRun(run);log.setLevel("SUCCESS");log.setMessage(summary);log.setDetailsJson(write(Map.of("matchedCount",count)));logs.save(log);
        if(count>0){AutomationNotification n=new AutomationNotification();n.setCompany(rule.getCompany());n.setRule(rule);n.setRun(run);n.setChannel(rule.getNotificationChannel());n.setNotificationType("WARNING");n.setTitle(rule.getName());n.setMessage(summary);n.setRecipient(actor);n.setDeliveryStatus(rule.getNotificationChannel().equals("IN_APP")?"DELIVERED":"READY");notifications.save(n);}
        audit.record(rule.getCompany(),actor,"AUTOMATION_RULE_RUN","AUTOMATION_RUN",run.getId(),Map.of("matchedCount",count,"status","SUCCESS","correlationId",run.getCorrelationId()));
        memory.record(rule.getCompany(),actor.getId(),"AUTOMATION_RULE_RUN","AUTOMATION_RUN",run.getId(),Map.of("ruleType",rule.getRuleType(),"matchedCount",count,"summary",summary));
        return response(run);
    }
    @Transactional public int runDue(){int count=0;for(AutomationRule due:rules.findAllByActiveTrueAndNextRunAtLessThanEqual(Instant.now())){execute(due,due.getCreatedBy(),"SCHEDULED");count++;}return count;}
    @Transactional(readOnly=true) public List<RunResponse> runs(UUID userId,UUID companyId){access.requireMembership(companyId,userId);return runs.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::response).toList();}
    @Transactional(readOnly=true) public List<NotificationResponse> notifications(UUID userId,UUID companyId){access.requireMembership(companyId,userId);return notifications.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::response).toList();}
    @Transactional public NotificationResponse read(UUID userId,UUID companyId,UUID id){access.requireMembership(companyId,userId);AutomationNotification n=notifications.findByIdAndCompanyId(id,companyId).orElseThrow(()->new NotFoundException("Notification not found."));n.setReadAt(Instant.now());return response(n);}
    @Transactional(readOnly=true) public AutomationDashboard dashboard(UUID userId,UUID companyId){access.requireMembership(companyId,userId);List<AutomationRule> rs=rules.findAllByCompanyIdOrderByCreatedAtDesc(companyId);List<AutomationRun> history=runs.findAllByCompanyIdOrderByCreatedAtDesc(companyId);return new AutomationDashboard(rs.stream().filter(AutomationRule::isActive).count(),history.size(),notifications.countByCompanyIdAndReadAtIsNull(companyId),history.stream().filter(r->"FAILED".equals(r.getStatus())).count());}

    private int matchCount(AutomationRule r){UUID id=r.getCompany().getId();return switch(r.getRuleType()){
        case "LOW_STOCK" -> inventoryAlerts.findAllByCompanyIdAndResolvedFalseOrderByCreatedAtDesc(id).size();
        case "BANK_UNRECONCILED" -> Math.toIntExact(bankTransactions.countByCompanyIdAndReconciliationStatus(id,ReconciliationStatus.UNMATCHED));
        case "RECEIVABLE_OVERDUE" -> (int)invoices.findAllByCompanyIdAndStatusInOrderByDueDate(id,List.of(InvoiceStatus.APPROVED,InvoiceStatus.POSTED)).stream().filter(i->i.getDueDate().isBefore(LocalDate.now())).count();
        case "GST_DUE" -> dueWithin(map(r.getParametersJson()));
        case "MONTH_END" -> LocalDate.now().plusDays(longValue(map(r.getParametersJson()).get("daysBefore"),2)).getMonth()!=LocalDate.now().getMonth()?1:0;
        default -> 0;};}
    private int dueWithin(Map<String,Object> p){Object value=p.get("dueDate");if(value==null)return 0;try{LocalDate due=LocalDate.parse(value.toString());long days=longValue(p.get("daysBefore"),5);return !due.isBefore(LocalDate.now())&&!due.isAfter(LocalDate.now().plusDays(days))?1:0;}catch(RuntimeException e){return 0;}}
    private long longValue(Object v,long fallback){try{return v==null?fallback:Long.parseLong(v.toString());}catch(NumberFormatException e){return fallback;}}
    private String summary(String type,int count){return count==0?"No action is currently required.":switch(type){case "LOW_STOCK"->count+" low-stock alert(s) require review.";case "BANK_UNRECONCILED"->count+" bank transaction(s) remain unreconciled.";case "RECEIVABLE_OVERDUE"->count+" receivable(s) are overdue.";case "GST_DUE"->"GST filing is due within the configured reminder window.";default->"Month-end review checklist is ready.";};}
    private Instant next(String s,Instant from){ZonedDateTime z=from.atZone(ZoneOffset.UTC);return (switch(s){case "WEEKLY"->z.plusWeeks(1);case "MONTHLY"->z.plusMonths(1);case "FINANCIAL_YEAR"->z.plusYears(1);case "EVENT_TRIGGER"->z.plusYears(100);default->z.plusDays(1);}).toInstant();}
    private RuleResponse response(AutomationRule r){return new RuleResponse(r.getId(),r.getName(),r.getRuleType(),r.getScheduleType(),map(r.getParametersJson()),r.getNotificationChannel(),r.isActive(),r.getNextRunAt(),r.getLastRunAt());}
    private RunResponse response(AutomationRun r){return new RunResponse(r.getId(),r.getRule().getId(),r.getStatus(),r.getMatchedCount(),r.getSummary(),r.getCorrelationId(),r.getStartedAt(),r.getCompletedAt());}
    private NotificationResponse response(AutomationNotification n){return new NotificationResponse(n.getId(),n.getChannel(),n.getNotificationType(),n.getTitle(),n.getMessage(),n.getDeliveryStatus(),n.getReadAt(),n.getCreatedAt());}
    private AutomationRule rule(UUID c,UUID id){return rules.findByIdAndCompanyId(id,c).orElseThrow(()->new NotFoundException("Automation rule not found."));}
    private Company company(UUID id){return companies.findById(id).orElseThrow(()->new NotFoundException("Company not found."));} private User user(UUID id){return users.findById(id).orElseThrow(()->new NotFoundException("Account not found."));}
    private String allowed(String v,Set<String> options,String message){String x=v.trim().toUpperCase(Locale.ROOT);if(!options.contains(x))throw new AccountingRuleException(message);return x;}
    private String write(Object value){try{return json.writeValueAsString(value==null?Map.of():value);}catch(Exception e){throw new AccountingRuleException("Automation parameters are invalid.");}}
    private Map<String,Object> map(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){return Map.of();}}
    private String correlation(){String id=MDC.get("correlationId");return id==null?UUID.randomUUID().toString():id;}
}
