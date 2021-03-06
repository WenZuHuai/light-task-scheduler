package com.lts.tasktracker.runner;

import com.lts.core.cluster.Config;
import com.lts.core.cluster.LTSConfig;
import com.lts.core.constant.Environment;
import com.lts.core.domain.Job;
import com.lts.core.domain.JobWrapper;
import com.lts.core.factory.NamedThreadFactory;
import com.lts.core.json.JSON;
import com.lts.ec.injvm.InjvmEventCenter;
import com.lts.tasktracker.domain.Response;
import com.lts.tasktracker.domain.TaskTrackerAppContext;
import com.lts.tasktracker.expcetion.NoAvailableJobRunnerException;
import com.lts.tasktracker.monitor.TaskTrackerMStatReporter;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Robert HG (254963746@qq.com) on 2/21/16.
 */
public class RunnerPoolTest {

    @Test
    public void testInterruptor() throws NoAvailableJobRunnerException {

        LTSConfig.setEnvironment(Environment.UNIT_TEST);

        Config config = new Config();
        config.setWorkThreads(10);
        config.setIdentity("fjdaslfjlasj");

        TaskTrackerAppContext appContext = new TaskTrackerAppContext();
        appContext.setConfig(config);
        appContext.setEventCenter(new InjvmEventCenter());
        appContext.setJobRunnerClass(TestInterruptorJobRunner.class);
//        appContext.setJobRunnerClass(NormalJobRunner.class);

        RunnerPool runnerPool = new RunnerPool(appContext);

        appContext.setRunnerPool(runnerPool);

        TaskTrackerMStatReporter monitor = new TaskTrackerMStatReporter(appContext);
        appContext.setMStatReporter(monitor);

        RunnerCallback callback = new RunnerCallback() {

            @Override
            public JobWrapper runComplete(Response response) {
                System.out.println("complete:" + JSON.toJSONString(response));
                return null;
            }
        };

        Job job = new Job();
        job.setTaskId("fdsafas");

        JobWrapper jobWrapper = new JobWrapper();
        jobWrapper.setJobId("111111");
        jobWrapper.setJob(job);

        runnerPool.execute(jobWrapper, callback);
        System.out.println(runnerPool.getAvailablePoolSize());

        try {
            Thread.sleep(5000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 5s之后停止
        runnerPool.stopWorking();

        while (true) {
            try {
                // 如果这个数字还在增长,表示线程还在执行,测试发现 NormalJobRunner 确实还在执行  TestInterruptorJobRunner 会停止
                System.out.println(NormalJobRunner.l);
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(runnerPool.getAvailablePoolSize());
        }
    }

    @Test
    public void test() throws NoAvailableJobRunnerException {
        int workThreads = 5;
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(workThreads, workThreads, 30, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),           // 直接提交给线程而不保持它们
                new NamedThreadFactory("test"),
                new ThreadPoolExecutor.AbortPolicy());

        final List<Thread> list = new CopyOnWriteArrayList<Thread>();

        for (int i = 0; i < 12; i++) {
            submitJob(threadPoolExecutor, list);
        }

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        list.get(0).interrupt();

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        submitJob(threadPoolExecutor, list);
        submitJob(threadPoolExecutor, list);

        try {
            Thread.sleep(5000000000000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void submitJob(ThreadPoolExecutor threadPoolExecutor, final List<Thread> list) {
        try {
            threadPoolExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    list.add(Thread.currentThread());
                    try {
                        while (true) {
                            Thread.sleep(2000L);
                            System.out.println("=====" + Thread.currentThread().getName());

                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            System.out.println("-- " + e.getMessage());
        }
    }

}