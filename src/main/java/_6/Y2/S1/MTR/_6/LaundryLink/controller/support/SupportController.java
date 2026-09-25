package _6.Y2.S1.MTR._6.LaundryLink.controller.support;

import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess;
import _6.Y2.S1.MTR._6.LaundryLink.service.support.ReportService;
import _6.Y2.S1.MTR._6.LaundryLink.service.support.AdministrationService;
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
    private final ReportService reports;
    private final AdministrationService administration;
    public SupportController(SupportAccess access,SupportService support,ReportService reports,AdministrationService administration) {
        this.access=access;this.support=support;this.reports=reports;this.administration=administration;
    }
    @GetMapping("/context")
    public Object context(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo) { return support.options(access.actor(p,demo)); }
    @GetMapping("/cases")
    public Object list(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@RequestParam(required=false) String search,@RequestParam(required=false) String status,@RequestParam(required=false) String type,@RequestParam(required=false) String priority,@RequestParam(required=false) Integer assigneeId,@RequestParam(defaultValue="0") int page) { return support.cases(access.actor(p,demo),search,status,type,priority,assigneeId,page); }
    @GetMapping("/cases/summary")
    public Object summary(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo) { return support.summary(access.actor(p,demo)); }
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
    @GetMapping("/reports")
    public Object report(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) Integer serviceId) { return reports.report(access.actor(p,demo),from,to,serviceId); }
    @GetMapping("/orders")
    public Object orders(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@RequestParam(required=false) Integer orderId,@RequestParam(defaultValue="0") int page) { return reports.orders(access.actor(p,demo),orderId,page); }
    @GetMapping("/orders/{id}/history")
    public Object history(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id) { return reports.orderHistory(access.actor(p,demo),id); }
    @GetMapping("/administration/catalog")
    public Object catalog(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo) { return administration.catalog(access.actor(p,demo)); }
    @PostMapping("/administration/services")
    @ResponseStatus(HttpStatus.CREATED)
    public Object createService(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@Valid @RequestBody ServiceInput input) { return Map.of("id",administration.createService(access.actor(p,demo),input)); }
    @PutMapping("/administration/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateService(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id,@Valid @RequestBody ServiceInput input) { administration.updateService(access.actor(p,demo),id,input); }
    @PutMapping("/administration/services/{id}/prices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPrice(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id,@Valid @RequestBody PriceInput input) { administration.setPrice(access.actor(p,demo),id,input); }
    @GetMapping("/administration/staff")
    public Object staff(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@RequestParam(required=false) String search) { return administration.staff(access.actor(p,demo),search); }
    @PostMapping("/administration/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public Object createStaff(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@Valid @RequestBody StaffInput input) { return Map.of("id",administration.createStaff(access.actor(p,demo),input)); }
    @PutMapping("/administration/staff/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStaff(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo,@PathVariable int id,@Valid @RequestBody StaffInput input) { administration.updateStaff(access.actor(p,demo),id,input); }
    @GetMapping("/administration/roles")
    public Object roles(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo) { return administration.roles(access.actor(p,demo)); }
    @GetMapping("/administration/activity")
    public Object administrationActivity(Principal p,@RequestHeader(value="X-Demo-User",required=false) Integer demo) { return administration.activity(access.actor(p,demo)); }
}
