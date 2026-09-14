package _6.Y2.S1.MTR._6.LaundryLink.service.support;

import _6.Y2.S1.MTR._6.LaundryLink.repository.support.SupportRepository;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess.Actor;
import _6.Y2.S1.MTR._6.LaundryLink.dto.support.SupportRequests.SettingInput;

@Service
@Transactional(readOnly = true)
public class SettingsService {
    private final SupportRepository repo;
    private final SupportAccess access;
    public SettingsService(SupportRepository repo, SupportAccess access) { this.repo=repo; this.access=access; }
    public List<Map<String,Object>> list(Actor actor) {
        access.manager(actor);
        return repo.query("SELECT settingID AS id, settingKey AS [key], settingValue AS value, description, updatedAt, version FROM system_settings ORDER BY settingKey");
    }
    public List<Map<String,Object>> activity(Actor actor) {
        access.manager(actor);
        return repo.query("SELECT TOP 100 a.action, a.details, a.createdAt, CONCAT(u.firstName,' ',u.lastName) AS actor FROM support_activity a JOIN users u ON u.userID=a.actorID WHERE a.feedbackID IS NULL ORDER BY a.activityID DESC");
    }
    @Transactional
    public int create(Actor actor, SettingInput input) {
        access.manager(actor);
        int id=repo.insert("INSERT INTO system_settings(settingKey,settingValue,description,updatedBy) OUTPUT INSERTED.settingID VALUES (?,?,?,?)", input.key(),input.value().trim(),input.description().trim(),actor.id());
        repo.audit(null,actor.id(),"Setting created",input.key());
        return id;
    }
    @Transactional
    public void edit(Actor actor,int id,SettingInput input) {
        access.manager(actor);
        SupportService.changed(repo.update("UPDATE system_settings SET settingKey=?,settingValue=?,description=?,updatedBy=?,updatedAt=SYSDATETIME(),version=version+1 WHERE settingID=? AND version=?",input.key(),input.value().trim(),input.description().trim(),actor.id(),id,input.version()));
        repo.audit(null,actor.id(),"Setting updated",input.key());
    }
    @Transactional
    public void delete(Actor actor,int id,int version) {
        access.manager(actor);
        var rows=repo.query("SELECT settingKey FROM system_settings WHERE settingID=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND,"Setting not found.");
        SupportService.changed(repo.update("DELETE FROM system_settings WHERE settingID=? AND version=?",id,version));
        repo.audit(null,actor.id(),"Setting deleted",rows.getFirst().get("settingKey").toString());
    }
}
