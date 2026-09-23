package _6.Y2.S1.MTR._6.LaundryLink.controller.support;

import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess;
import _6.Y2.S1.MTR._6.LaundryLink.service.support.ReportService;
import _6.Y2.S1.MTR._6.LaundryLink.service.support.SettingsService;
import _6.Y2.S1.MTR._6.LaundryLink.service.support.SupportService;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static _6.Y2.S1.MTR._6.LaundryLink.dto.support.SupportRequests.*;

@RestController
@RequestMapping("/api/support")
public class SupportController {
    private final SupportAccess access;
    private final SupportService support;
    private final SettingsService settings;
    private final ReportService reports;
    public SupportController(SupportAccess access,SupportService support,SettingsService settings,ReportService reports) {
        this.access=access;this.support=support;this.settings=settings;this.reports=reports;
    }
    @GetMapping("/context")
    public Object context(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo) { return support.options(access.actor(p,demo)); }
    @GetMapping("/cases")
    public Object list(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@RequestParam(required=false) String search,@RequestParam(required=false) String status,@RequestParam(required=false) String type,@RequestParam(defaultValue="0") int page) { return support.cases(access.actor(p,demo),search,status,type,page); }
    @GetMapping("/cases/{id}")
    public Object detail(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id) { return support.detail(access.actor(p,demo),id); }
    @PostMapping("/cases")
    public ResponseEntity<?> create(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@Valid @RequestBody CaseInput input) { return ResponseEntity.status(201).body(support.create(access.actor(p,demo),input)); }
    @PutMapping("/cases/{id}")
    public Object edit(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id,@Valid @RequestBody CaseInput input) { return support.edit(access.actor(p,demo),id,input); }
    @DeleteMapping("/cases/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id,@RequestParam int version) { support.delete(access.actor(p,demo),id,version); }
    @PatchMapping("/cases/{id}")
    public Object handle(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id,@Valid @RequestBody CaseUpdate input) { return support.handle(access.actor(p,demo),id,input); }
    @PostMapping("/cases/{id}/messages")
    public Object message(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id,@Valid @RequestBody MessageInput input) { return support.message(access.actor(p,demo),id,input); }
    @GetMapping("/settings")
    public Object settings(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo) { return settings.list(access.actor(p,demo)); }
    @PostMapping("/settings")
    @ResponseStatus(HttpStatus.CREATED)
    public Object createSetting(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@Valid @RequestBody SettingInput input) { return Map.of("id",settings.create(access.actor(p,demo),input)); }
    @PutMapping("/settings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void editSetting(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id,@Valid @RequestBody SettingInput input) { settings.edit(access.actor(p,demo),id,input); }
    @DeleteMapping("/settings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSetting(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id,@RequestParam int version) { settings.delete(access.actor(p,demo),id,version); }
    @GetMapping("/activity")
    public Object activity(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo) { return settings.activity(access.actor(p,demo)); }
    @GetMapping("/reports")
    public Object report(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) Integer serviceId) { return reports.report(access.actor(p,demo),from,to,serviceId); }
    @GetMapping("/orders")
    public Object orders(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@RequestParam(required=false) Integer orderId,@RequestParam(defaultValue="0") int page) { return reports.orders(access.actor(p,demo),orderId,page); }
    @GetMapping("/orders/{id}/history")
    public Object history(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id) { return reports.orderHistory(access.actor(p,demo),id); }
}
