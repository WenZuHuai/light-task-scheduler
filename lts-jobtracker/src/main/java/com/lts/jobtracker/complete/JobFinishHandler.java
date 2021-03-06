package com.lts.jobtracker.complete;

import com.lts.core.commons.utils.CollectionUtils;
import com.lts.core.domain.JobWrapper;
import com.lts.core.domain.TaskTrackerJobResult;
import com.lts.core.json.JSON;
import com.lts.core.logger.Logger;
import com.lts.core.logger.LoggerFactory;
import com.lts.core.support.LoggerName;
import com.lts.core.support.SystemClock;
import com.lts.jobtracker.domain.JobTrackerAppContext;
import com.lts.core.support.CronExpressionUtils;
import com.lts.queue.domain.JobPo;
import com.lts.store.jdbc.exception.DupEntryException;

import java.util.Date;
import java.util.List;

/**
 * @author Robert HG (254963746@qq.com) on 11/11/15.
 */
public class JobFinishHandler implements JobCompleteHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerName.JobTracker);

    private JobTrackerAppContext appContext;

    public JobFinishHandler(JobTrackerAppContext appContext) {
        this.appContext = appContext;
    }

    @Override
    public void onComplete(List<TaskTrackerJobResult> results) {
        if (CollectionUtils.isEmpty(results)) {
            return;
        }

        for (TaskTrackerJobResult result : results) {

            JobWrapper jobWrapper = result.getJobWrapper();

            if (jobWrapper.getJob().isSchedule()) {
                finishScheduleJob(jobWrapper.getJobId());
            }
            // 从正在执行的队列中移除
            appContext.getExecutingJobQueue().remove(jobWrapper.getJobId());
        }
    }

    private void finishScheduleJob(String jobId) {
        JobPo cronJobPo = appContext.getCronJobQueue().finish(jobId);
        if (cronJobPo == null) {
            // 可能任务队列中改条记录被删除了
            return;
        }
        Date nextTriggerTime = CronExpressionUtils.getNextTriggerTime(cronJobPo.getCronExpression());
        if (nextTriggerTime == null) {
            // 从CronJob队列中移除
            appContext.getCronJobQueue().remove(jobId);
            return;
        }
        // 表示下次还要执行
        try {
            cronJobPo.setTaskTrackerIdentity(null);
            cronJobPo.setIsRunning(false);
            cronJobPo.setTriggerTime(nextTriggerTime.getTime());
            cronJobPo.setGmtModified(SystemClock.now());
            appContext.getExecutableJobQueue().add(cronJobPo);
        } catch (DupEntryException e) {
            LOGGER.warn("ExecutableJobQueue already exist:" + JSON.toJSONString(cronJobPo));
        }
    }
}
