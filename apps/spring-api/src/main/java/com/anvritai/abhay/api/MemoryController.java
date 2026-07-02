package com.anvritai.abhay.api;

import com.anvritai.abhay.api.MemoryDtos.*;
import com.anvritai.abhay.security.UserPrincipal;
import com.anvritai.abhay.service.MemoryOsService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/memory")
public class MemoryController {
    private final MemoryOsService memory;
    public MemoryController(MemoryOsService memory){this.memory=memory;}

    @GetMapping("/dashboard") public MemoryDashboardResponse dashboard(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId){return memory.dashboard(p.id(),companyId);}
    @GetMapping("/events") public List<MemoryEventResponse> events(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId){return memory.events(p.id(),companyId);}
    @GetMapping("/patterns") public List<MemoryPatternResponse> patterns(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId){return memory.patterns(p.id(),companyId);}
    @GetMapping("/suggestions/ledger") public MemorySuggestionResponse ledger(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId,@RequestParam String subject){return memory.suggestLedger(p.id(),companyId,subject);}
    @GetMapping("/suggestions/gst") public MemorySuggestionResponse gst(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId,@RequestParam String subject){return memory.suggestGst(p.id(),companyId,subject);}
    @GetMapping("/suggestions/voucher") public MemorySuggestionResponse voucher(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId,@RequestParam String subject){return memory.suggestVoucher(p.id(),companyId,subject);}
    @GetMapping("/suggestions/document") public MemorySuggestionResponse document(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId,@RequestParam String fieldName,@RequestParam(required=false) String currentValue){return memory.suggestDocument(p.id(),companyId,fieldName,currentValue);}
    @PostMapping("/feedback") public MemoryFeedbackResponse feedback(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId,@Valid @RequestBody MemoryFeedbackRequest request){return memory.feedback(p.id(),companyId,request);}
    @PostMapping("/rebuild-patterns") public PatternRebuildResponse rebuild(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId){return memory.rebuild(p.id(),companyId);}
    @PostMapping("/export") public MemoryExportResponse export(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId){return memory.export(p.id(),companyId);}
    @PostMapping("/purge") public MemoryPurgeResponse purge(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID companyId,@Valid @RequestBody MemoryPurgeRequest request){return memory.purge(p.id(),companyId,request);}
}
