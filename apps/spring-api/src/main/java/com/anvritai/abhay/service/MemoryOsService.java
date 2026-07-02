package com.anvritai.abhay.service;

import com.anvritai.abhay.api.MemoryDtos.*;
import com.anvritai.abhay.domain.*;
import com.anvritai.abhay.domain.memory.*;
import com.anvritai.abhay.repository.*;
import com.anvritai.abhay.repository.memory.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryOsService {
    private static final RoleCode[] WRITE_ROLES={RoleCode.OWNER,RoleCode.ADMIN,RoleCode.ACCOUNTANT};
    private static final BigDecimal LOW_THRESHOLD=new BigDecimal("0.7000");
    private final CompanyAccessService access; private final CompanyRepository companies; private final UserRepository users;
    private final MemoryProfileRepository profiles; private final MemoryEventRepository events;
    private final MemoryPatternRepository patterns; private final MemoryPreferenceRepository preferences;
    private final MemorySuggestionRepository suggestions; private final MemoryFeedbackRepository feedback;
    private final MemoryConfidenceScoreRepository scores; private final MemoryExplanationRepository explanations;
    private final MemoryEventCaptureService capture; private final AuditService audit; private final ObjectMapper mapper;

    public MemoryOsService(CompanyAccessService access,CompanyRepository companies,UserRepository users,
      MemoryProfileRepository profiles,MemoryEventRepository events,MemoryPatternRepository patterns,
      MemoryPreferenceRepository preferences,MemorySuggestionRepository suggestions,
      MemoryFeedbackRepository feedback,MemoryConfidenceScoreRepository scores,
      MemoryExplanationRepository explanations,MemoryEventCaptureService capture,AuditService audit,ObjectMapper mapper){
      this.access=access;this.companies=companies;this.users=users;this.profiles=profiles;this.events=events;
      this.patterns=patterns;this.preferences=preferences;this.suggestions=suggestions;this.feedback=feedback;
      this.scores=scores;this.explanations=explanations;this.capture=capture;this.audit=audit;this.mapper=mapper;
    }

    @Transactional(readOnly=true)
    public MemoryDashboardResponse dashboard(UUID userId,UUID companyId){
      access.requireMembership(companyId,userId);
      List<MemoryEvent> companyEvents=events.findAllByCompanyIdOrderByOccurredAtDesc(companyId);
      List<MemoryPattern> companyPatterns=patterns.findAllByCompanyIdOrderByConfidenceScoreDesc(companyId);
      Map<MemoryType,Long> byType=new EnumMap<>(MemoryType.class);
      companyEvents.forEach(e->byType.merge(e.getMemoryType(),1L,Long::sum));
      BigDecimal average=companyPatterns.isEmpty()?BigDecimal.ZERO.setScale(4):companyPatterns.stream()
        .map(MemoryPattern::getConfidenceScore).reduce(BigDecimal.ZERO,BigDecimal::add)
        .divide(BigDecimal.valueOf(companyPatterns.size()),4,RoundingMode.HALF_UP);
      return new MemoryDashboardResponse(companyEvents.size(),companyPatterns.size(),suggestions.countByCompanyId(companyId),
        feedback.countByCompanyId(companyId),companyPatterns.stream().filter(p->p.getConfidenceScore().compareTo(LOW_THRESHOLD)<0).count(),
        average,profiles.findByCompanyId(companyId).map(MemoryProfile::getLastRebuiltAt).orElse(null),byType);
    }

    @Transactional(readOnly=true)
    public List<MemoryEventResponse> events(UUID userId,UUID companyId){
      access.requireMembership(companyId,userId);
      return events.findAllByCompanyIdOrderByOccurredAtDesc(companyId).stream().map(this::eventResponse).toList();
    }

    @Transactional(readOnly=true)
    public List<MemoryPatternResponse> patterns(UUID userId,UUID companyId){
      access.requireMembership(companyId,userId);
      return patterns.findAllByCompanyIdOrderByConfidenceScoreDesc(companyId).stream().map(this::patternResponse).toList();
    }

    @Transactional
    public PatternRebuildResponse rebuild(UUID userId,UUID companyId){
      access.requireRole(companyId,userId,WRITE_ROLES);
      Company company=company(companyId); List<MemoryEvent> source=events.findAllByCompanyIdOrderByOccurredAtAsc(companyId);
      Map<String,Aggregate> aggregates=new LinkedHashMap<>();
      for(MemoryEvent event:source){
        Map<String,Object> context=context(event);
        for(Evidence evidence:derive(event,context)){
          String key=patternKey(evidence);
          aggregates.computeIfAbsent(key,k->new Aggregate(evidence)).add(event);
        }
      }
      for(Map.Entry<String,Aggregate> entry:aggregates.entrySet()) upsertPattern(company,entry.getKey(),entry.getValue());
      MemoryProfile profile=profiles.findByCompanyId(companyId).orElseGet(()->{MemoryProfile p=new MemoryProfile();p.setCompany(company);return p;});
      Instant rebuilt=Instant.now();profile.setLastRebuiltAt(rebuilt);profiles.save(profile);
      audit.record(company,user(userId),"MEMORY_PATTERNS_REBUILT","MEMORY_PROFILE",profile.getId(),
        Map.of("eventsProcessed",source.size(),"patterns",aggregates.size()));
      return new PatternRebuildResponse(source.size(),aggregates.size(),rebuilt);
    }

    @Transactional public MemorySuggestionResponse suggestLedger(UUID u,UUID c,String subject){return suggest(u,c,"LEDGER",subject);}
    @Transactional public MemorySuggestionResponse suggestGst(UUID u,UUID c,String subject){return suggest(u,c,"GST_TREATMENT",subject);}
    @Transactional public MemorySuggestionResponse suggestVoucher(UUID u,UUID c,String subject){return suggest(u,c,"VOUCHER_TYPE",subject);}
    @Transactional public MemorySuggestionResponse suggestDocument(UUID u,UUID c,String field,String current){
      String subject=normalize(field); return suggest(u,c,"DOCUMENT_FIELD",subject);
    }

    @Transactional
    public MemoryFeedbackResponse feedback(UUID userId,UUID companyId,MemoryFeedbackRequest request){
      access.requireRole(companyId,userId,WRITE_ROLES);
      MemorySuggestion suggestion=suggestions.findByIdAndCompanyId(request.suggestionId(),companyId)
        .orElseThrow(()->new NotFoundException("Memory suggestion was not found."));
      if(feedback.existsBySuggestionId(suggestion.getId())) throw new ConflictException("Feedback was already recorded for this suggestion.");
      if(request.action()==MemoryFeedbackAction.CORRECTED&&(request.correctedValue()==null||request.correctedValue().isBlank()))
        throw new AccountingRuleException("Corrected value is required for correction feedback.");
      MemoryFeedback entity=new MemoryFeedback();entity.setCompany(company(companyId));entity.setSuggestion(suggestion);
      entity.setUser(user(userId));entity.setAction(request.action());entity.setCorrectedValue(trim(request.correctedValue(),1000));
      entity.setComment(trim(request.comment(),1000));entity=feedback.save(entity);
      MemoryPattern pattern=suggestion.getPattern();
      MemoryPattern effectivePattern=pattern;
      if(pattern!=null){
        if(request.action()==MemoryFeedbackAction.REJECTED)pattern.setFailureCount(pattern.getFailureCount()+1);
        else if(request.action()==MemoryFeedbackAction.ACCEPTED)pattern.setSuccessCount(pattern.getSuccessCount()+1);
        else {
          pattern.setFailureCount(pattern.getFailureCount()+1);
          effectivePattern=correctedPattern(pattern,request.correctedValue().trim());
        }
        pattern.setLastUsedAt(Instant.now());pattern.setConfidenceScore(confidence(pattern));patterns.save(pattern);snapshot(pattern,"User feedback: "+request.action());
      }
      suggestion.setStatus(MemorySuggestionStatus.valueOf(request.action().name()));suggestions.save(suggestion);
      Map<String,Object> detail=new LinkedHashMap<>();detail.put("suggestionType",suggestion.getSuggestionType());
      detail.put("inputKey",suggestion.getInputKey());detail.put("action",request.action().name());
      if(request.correctedValue()!=null)detail.put("correctedValue",request.correctedValue());
      capture.record(company(companyId),userId,"MEMORY_FEEDBACK_RECORDED","MEMORY_SUGGESTION",suggestion.getId(),detail);
      audit.record(company(companyId),user(userId),"MEMORY_FEEDBACK_RECORDED","MEMORY_SUGGESTION",suggestion.getId(),detail);
      return new MemoryFeedbackResponse(entity.getId(),suggestion.getId(),request.action(),
        effectivePattern==null?BigDecimal.ZERO.setScale(4):effectivePattern.getConfidenceScore(),
        effectivePattern==null?suggestion.getSuggestedValue():effectivePattern.getSuggestedValue());
    }

    @Transactional(readOnly=true)
    public MemoryExportResponse export(UUID userId,UUID companyId){
      access.requireRole(companyId,userId,RoleCode.OWNER,RoleCode.ADMIN);
      MemoryExportResponse response=new MemoryExportResponse(companyId,Instant.now(),
        events.findAllByCompanyIdOrderByOccurredAtDesc(companyId).stream().map(this::eventResponse).toList(),
        patterns.findAllByCompanyIdOrderByConfidenceScoreDesc(companyId).stream().map(this::patternResponse).toList());
      audit.record(company(companyId),user(userId),"MEMORY_EXPORTED","COMPANY",companyId,
        Map.of("events",response.events().size(),"patterns",response.patterns().size()));
      return response;
    }

    @Transactional
    public MemoryPurgeResponse purge(UUID userId,UUID companyId,MemoryPurgeRequest request){
      access.requireRole(companyId,userId,RoleCode.OWNER);
      if(request==null||!"PURGE".equals(request.confirmation()))throw new AccountingRuleException("Type PURGE to confirm memory deletion.");
      long eventCount=events.countByCompanyId(companyId),patternCount=patterns.countByCompanyId(companyId);
      long suggestionCount=suggestions.countByCompanyId(companyId),feedbackCount=feedback.countByCompanyId(companyId);
      explanations.deleteAllByCompanyId(companyId);feedback.deleteAllByCompanyId(companyId);scores.deleteAllByCompanyId(companyId);
      suggestions.deleteAllByCompanyId(companyId);patterns.deleteAllByCompanyId(companyId);preferences.deleteAllByCompanyId(companyId);
      events.deleteAllByCompanyId(companyId);profiles.deleteAllByCompanyId(companyId);
      audit.record(company(companyId),user(userId),"MEMORY_PURGED","COMPANY",companyId,
        Map.of("eventsDeleted",eventCount,"patternsDeleted",patternCount));
      return new MemoryPurgeResponse(eventCount,patternCount,suggestionCount,feedbackCount);
    }

    private MemorySuggestionResponse suggest(UUID userId,UUID companyId,String type,String input){
      access.requireMembership(companyId,userId);String normalized=normalize(input);
      if(normalized.isBlank())throw new AccountingRuleException("Suggestion input is required.");
      MemoryPattern pattern=patterns.findAllByCompanyIdAndSuggestionTypeAndSubjectKeyIgnoreCaseOrderByConfidenceScoreDesc(companyId,type,normalized)
        .stream().findFirst().orElse(null);
      BigDecimal confidence=pattern==null?BigDecimal.ZERO.setScale(4):pattern.getConfidenceScore();boolean low=confidence.compareTo(LOW_THRESHOLD)<0;
      String reason=pattern==null?"No matching company memory pattern has been validated yet.":pattern.getExplanation();
      String warning=low?"Low confidence: review this suggestion carefully before using it.":null;
      MemorySuggestion suggestion=new MemorySuggestion();suggestion.setCompany(company(companyId));suggestion.setPattern(pattern);
      suggestion.setSuggestionType(type);suggestion.setInputKey(normalized);suggestion.setSuggestedValue(pattern==null?null:pattern.getSuggestedValue());
      suggestion.setConfidenceScore(confidence);suggestion.setLowConfidence(low);suggestion.setSupportingEventCount(pattern==null?0:pattern.getOccurrenceCount());
      suggestion.setLastSimilarAt(pattern==null?null:pattern.getLastSimilarAt());suggestion.setCreatedBy(user(userId));suggestion=suggestions.save(suggestion);
      MemoryExplanation explanation=new MemoryExplanation();explanation.setCompany(company(companyId));explanation.setSuggestion(suggestion);
      explanation.setReason(reason);explanation.setSupportingEventCount(suggestion.getSupportingEventCount());
      explanation.setLastSimilarAt(suggestion.getLastSimilarAt());explanation.setWarning(warning);explanations.save(explanation);
      return new MemorySuggestionResponse(suggestion.getId(),type,normalized,suggestion.getSuggestedValue(),confidence,low,reason,
        suggestion.getSupportingEventCount(),suggestion.getLastSimilarAt(),warning,true);
    }

    private void upsertPattern(Company company,String key,Aggregate aggregate){
      MemoryPattern pattern=patterns.findByCompanyIdAndPatternKey(company.getId(),key).orElseGet(MemoryPattern::new);
      pattern.setCompany(company);pattern.setMemoryType(aggregate.evidence.type());pattern.setPatternKey(key);
      pattern.setSubjectKey(aggregate.evidence.subject());pattern.setSuggestionType(aggregate.evidence.suggestionType());
      pattern.setSuggestedValue(aggregate.evidence.value());pattern.setOccurrenceCount(aggregate.count);
      pattern.setLastSimilarAt(aggregate.last);pattern.setExplanation(aggregate.explanation());
      try{pattern.setEvidenceJson(mapper.writeValueAsString(Map.of("eventIds",aggregate.eventIds)));}
      catch(Exception e){throw new IllegalStateException("Pattern evidence could not be serialized.",e);}
      pattern.setConfidenceScore(confidence(pattern));pattern=patterns.save(pattern);snapshot(pattern,"Pattern rebuilt from append-only events.");
    }

    private MemoryPattern correctedPattern(MemoryPattern original,String correctedValue){
      Evidence evidence=new Evidence(original.getMemoryType(),original.getSuggestionType(),original.getSubjectKey(),correctedValue);
      String key=patternKey(evidence);
      MemoryPattern corrected=patterns.findByCompanyIdAndPatternKey(original.getCompany().getId(),key).orElseGet(MemoryPattern::new);
      corrected.setCompany(original.getCompany());corrected.setMemoryType(original.getMemoryType());corrected.setPatternKey(key);
      corrected.setSubjectKey(original.getSubjectKey());corrected.setSuggestionType(original.getSuggestionType());
      corrected.setSuggestedValue(correctedValue);corrected.setSuccessCount(corrected.getSuccessCount()+1);
      corrected.setLastUsedAt(Instant.now());corrected.setLastSimilarAt(Instant.now());
      corrected.setExplanation("User correction established this company-specific preference.");
      corrected.setEvidenceJson("{\"source\":\"user_feedback\"}");corrected.setConfidenceScore(confidence(corrected));
      corrected=patterns.save(corrected);snapshot(corrected,"User correction pattern.");return corrected;
    }

    private List<Evidence> derive(MemoryEvent event,Map<String,Object> c){
      List<Evidence> out=new ArrayList<>();String type=event.getEventType();
      if("VOUCHER_POSTED".equals(type)){
        add(out,MemoryType.VOUCHER_PATTERN_MEMORY,"VOUCHER_TYPE",string(c,"narration",event.getSubjectKey()),string(c,"voucherType"));
        Object mappings=c.get("ledgerMappings");if(mappings instanceof List<?> list)for(Object row:list)if(row instanceof Map<?,?> map)
          add(out,MemoryType.LEDGER_MAPPING_MEMORY,"LEDGER",string(c,"narration",event.getSubjectKey()),Objects.toString(map.get("ledgerId"),null));
      }else if("INVOICE_POSTED".equals(type)){
        String party=string(c,"party",event.getSubjectKey());
        MemoryType partyMemory="PURCHASE".equals(string(c,"invoiceType"))?MemoryType.VENDOR_MEMORY:MemoryType.CUSTOMER_MEMORY;
        add(out,partyMemory,"LEDGER",party,string(c,"partyLedgerId"));
        add(out,MemoryType.GST_TREATMENT_MEMORY,"GST_TREATMENT",party,string(c,"gstTreatment"));
        add(out,MemoryType.INVOICE_PATTERN_MEMORY,"VOUCHER_TYPE",party,string(c,"invoiceType"));
        Object invoiceItems=c.get("items");if(invoiceItems instanceof List<?> list)for(Object row:list)if(row instanceof Map<?,?> item){
          String itemName=Objects.toString(item.get("itemName"),null);
          add(out,MemoryType.INVOICE_PATTERN_MEMORY,"HSN_SAC",itemName,Objects.toString(item.get("hsnSac"),null));
          add(out,MemoryType.GST_TREATMENT_MEMORY,"GST_RATE",itemName,Objects.toString(item.get("gstRate"),null));
        }
      }else if(type.contains("DOCUMENT_FIELD_CORRECTED")){
        add(out,MemoryType.DOCUMENT_CORRECTION_MEMORY,"DOCUMENT_FIELD",string(c,"field"),string(c,"newValue"));
      }else if(type.contains("BANK_TRANSACTION_MATCHED")){
        add(out,MemoryType.BANK_RECONCILIATION_MEMORY,"LEDGER",string(c,"counterparty",string(c,"description",event.getSubjectKey())),string(c,"targetLedgerId",string(c,"targetId")));
      }else if(type.contains("STOCK_MOVEMENT")){
        add(out,MemoryType.INVENTORY_MEMORY,"INVENTORY_MOVEMENT",string(c,"itemName",event.getSubjectKey()),string(c,"movementType"));
      }else if(type.contains("GST")){
        add(out,MemoryType.GST_TREATMENT_MEMORY,"GST_TREATMENT",event.getSubjectKey(),string(c,"gstTreatment",string(c,"treatment")));
      }else if(type.contains("PAYMENT")){
        add(out,MemoryType.CUSTOMER_MEMORY,"VOUCHER_TYPE",string(c,"party",event.getSubjectKey()),string(c,"voucherType"));
      }else if(type.contains("MEMORY_FEEDBACK")){
        add(out,MemoryType.USER_PREFERENCE_MEMORY,"USER_PREFERENCE",string(c,"inputKey",event.getSubjectKey()),string(c,"correctedValue",string(c,"action")));
      }
      return out;
    }
    private void add(List<Evidence> list,MemoryType type,String suggestion,String subject,String value){
      String s=normalize(subject);String v=trim(value,1000);if(!s.isBlank()&&v!=null&&!v.isBlank())list.add(new Evidence(type,suggestion,s,v));
    }
    private BigDecimal confidence(MemoryPattern p){
      BigDecimal numerator=BigDecimal.valueOf(p.getOccurrenceCount()+p.getSuccessCount());
      BigDecimal denominator=BigDecimal.valueOf(p.getOccurrenceCount()+p.getSuccessCount()+p.getFailureCount()+1);
      return numerator.divide(denominator,4,RoundingMode.HALF_UP).min(BigDecimal.ONE.setScale(4));
    }
    private void snapshot(MemoryPattern p,String reason){MemoryConfidenceScore s=new MemoryConfidenceScore();s.setCompany(p.getCompany());s.setPattern(p);s.setConfidenceScore(p.getConfidenceScore());s.setOccurrenceCount(p.getOccurrenceCount());s.setSuccessCount(p.getSuccessCount());s.setFailureCount(p.getFailureCount());s.setReason(reason);s.setCalculatedAt(Instant.now());scores.save(s);}
    private Map<String,Object> context(MemoryEvent e){try{return mapper.readValue(e.getContextJson(),new TypeReference<>(){});}catch(Exception ex){return Map.of();}}
    private String patternKey(Evidence e){return trim(e.suggestionType()+"|"+e.subject()+"|"+e.value(),500);}
    private String normalize(String v){return v==null?"":v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
    private String string(Map<String,Object> map,String key){Object v=map.get(key);return v==null?null:v.toString();}
    private String string(Map<String,Object> map,String key,String fallback){String v=string(map,key);return v==null||v.isBlank()?fallback:v;}
    private String trim(String v,int max){if(v==null)return null;String clean=v.trim();return clean.length()<=max?clean:clean.substring(0,max);}
    private Company company(UUID id){return companies.findById(id).orElseThrow(()->new NotFoundException("Company was not found."));}
    private User user(UUID id){return users.findById(id).orElseThrow(()->new NotFoundException("User was not found."));}
    private MemoryEventResponse eventResponse(MemoryEvent e){return new MemoryEventResponse(e.getId(),e.getMemoryType(),e.getEventType(),e.getEntityType(),e.getEntityId(),e.getSubjectKey(),context(e),e.getOccurredAt());}
    private MemoryPatternResponse patternResponse(MemoryPattern p){return new MemoryPatternResponse(p.getId(),p.getMemoryType(),p.getSubjectKey(),p.getSuggestionType(),p.getSuggestedValue(),p.getOccurrenceCount(),p.getSuccessCount(),p.getFailureCount(),p.getConfidenceScore(),p.getLastSimilarAt(),p.getExplanation());}
    private record Evidence(MemoryType type,String suggestionType,String subject,String value){}
    private static final class Aggregate{private final Evidence evidence;private long count;private Instant last;private final List<UUID> eventIds=new ArrayList<>();Aggregate(Evidence e){evidence=e;}void add(MemoryEvent e){count++;last=e.getOccurredAt();if(eventIds.size()<20)eventIds.add(e.getId());}String explanation(){return "ABHAY observed "+count+" similar company event"+(count==1?"":"s")+" for "+evidence.subject()+".";}}
}
