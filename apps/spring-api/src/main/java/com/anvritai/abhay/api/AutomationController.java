package com.anvritai.abhay.api;
import static com.anvritai.abhay.api.AutomationDtos.*; import com.anvritai.abhay.security.UserPrincipal; import com.anvritai.abhay.service.AutomationService; import jakarta.validation.Valid; import java.util.*; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/companies/{companyId}/automation") public class AutomationController{
 private final AutomationService service;public AutomationController(AutomationService service){this.service=service;}
 @GetMapping("/dashboard") public AutomationDashboard dashboard(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId){return service.dashboard(p.id(),companyId);}
 @GetMapping("/templates") public List<TemplateResponse> templates(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId){return service.templates(p.id(),companyId);}
 @GetMapping("/rules") public List<RuleResponse> rules(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId){return service.rules(p.id(),companyId);}
 @PostMapping("/rules") public RuleResponse create(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId,@Valid @RequestBody CreateRuleRequest r){return service.create(p.id(),companyId,r);}
 @PatchMapping("/rules/{ruleId}") public RuleResponse update(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId,@PathVariable UUID ruleId,@Valid @RequestBody CreateRuleRequest r){return service.update(p.id(),companyId,ruleId,r);}
 @PostMapping("/rules/{ruleId}/run") public RunResponse run(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId,@PathVariable UUID ruleId){return service.run(p.id(),companyId,ruleId);}
 @GetMapping("/runs") public List<RunResponse> runs(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId){return service.runs(p.id(),companyId);}
 @GetMapping("/notifications") public List<NotificationResponse> notifications(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId){return service.notifications(p.id(),companyId);}
 @PostMapping("/notifications/{id}/read") public NotificationResponse read(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId,@PathVariable UUID id){return service.read(p.id(),companyId,id);}
}
