package com.mplatform.export.user.scheduledtasks;

import com.mplatform.export.user.service.ExportUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserExport {

    @Autowired
    private ExportUserService exportUserService;

    @Scheduled(cron = "#{@cronBean}")
    public void exportToMariaDb(){
        exportUserService.exportToMariaDb();
    }
}
