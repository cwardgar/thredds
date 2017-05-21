/*
 * Copyright 1998-2015 University Corporation for Atmospheric Research/Unidata
 *
 *   Portions of this software were developed by the Unidata Program at the
 *   University Corporation for Atmospheric Research.
 *
 *   Access and use of this software shall impose the following obligations
 *   and understandings on the user. The user is granted the right, without
 *   any fee or cost, to use, copy, modify, alter, enhance and distribute
 *   this software, and any derivative works thereof, and its supporting
 *   documentation for any purpose whatsoever, provided that this entire
 *   notice appears in all copies of the software, derivative works and
 *   supporting documentation.  Further, UCAR requests that the user credit
 *   UCAR/Unidata in any publications that result from the use of this
 *   software or in any product that includes this software. The names UCAR
 *   and/or Unidata, however, may not be used in any advertising or publicity
 *   to endorse or promote any products or commercial entity unless specific
 *   written permission is obtained from UCAR/Unidata. The user also
 *   understands that UCAR/Unidata is not obligated to provide the user with
 *   any support, consulting, training or assistance of any kind with regard
 *   to the use, operation and performance of this software nor to provide
 *   the user with any updates, revisions, new versions or "bug fixes."
 *
 *   THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *   IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *   WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *   DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *   INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *   FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *   NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *   WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package thredds.featurecollection;

import com.google.common.eventbus.EventBus;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import thredds.inventory.CollectionUpdateEvent;
import thredds.inventory.CollectionUpdateType;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Date;

/**
 * Handle background tasks for updating collections.
 * Singleton, thread safe.
 * Cover for quartz library.
 * Only used in tds/tdm.
 *
 * @author caron
 * @since Nov 21, 2010
 */
@ThreadSafe
@Component
public class CollectionUpdater {
  static private final org.slf4j.Logger fcLogger = org.slf4j.LoggerFactory.getLogger(CollectionUpdater.class);

  static private final String COLLECTION_NAME = "collectionName";
  static private final String EVENT_BUS = "eventBus";
  static private final String LOGGER = "logger";
  static private final String UpdateType = "updateType";
  static private final String Source = "Source";
  static private final long startupWait = 3 * 1000; // 3 secs
  static private boolean disabled = false;

  @Autowired
  @Qualifier("fcTriggerEventBus")
  private EventBus eventBus;

  private org.quartz.Scheduler scheduler = null;
  private boolean failed = false;
  private boolean isTdm = false;

  public void setTdm(boolean tdm) {
    isTdm = tdm;
  }

  public boolean isTdm() {
    return isTdm;
  }

  public CollectionUpdater() {
    try {
      scheduler = StdSchedulerFactory.getDefaultScheduler();
      scheduler.start();
      // scheduler.getListenerManager().addSchedulerListener(new MySchedListener());
    } catch (Throwable e) {
      failed = true;
      throw new RuntimeException("quartz scheduler failed to initialize", e);
    }
  }

  public void setEventBus(EventBus eventBus) {
    this.eventBus = eventBus;
  }


  // debugging
  public org.quartz.Scheduler getScheduler() {
    return scheduler;
  }

  /**
   * Look at config and decide if tasks need to be started
   */
  public void scheduleTasks(FeatureCollectionConfig config, Logger logger) {
    if (disabled || failed) return;
    if (logger == null) logger = fcLogger;

    FeatureCollectionConfig.UpdateConfig updateConfig = (isTdm) ? config.tdmConfig : config.updateConfig;
    if (updateConfig == null || updateConfig.updateType == CollectionUpdateType.never) return;

    String collectionName = config.getCollectionName();

    // prob dont need to set a job if theres no chron job ?

    // Job to update the collection
    org.quartz.JobDataMap map = new org.quartz.JobDataMap();
    map.put(EVENT_BUS, eventBus);
    map.put(COLLECTION_NAME, collectionName);
    map.put(LOGGER, logger);
    JobDetail updateJob = JobBuilder.newJob(UpdateCollectionJob.class)
            .withIdentity(collectionName, "UpdateCollection")
            .storeDurably()
            .usingJobData(map)
            .build();

    try {
      if(!scheduler.checkExists(updateJob.getKey())) {
      	scheduler.addJob(updateJob, false);
      } else {
        logger.warn("scheduler failed to add updateJob for " + updateJob.getKey() +". Another Job exists with that identification." );
      }
    } catch (Throwable e) {
      logger.error("scheduler failed to add updateJob for " + config, e);
      return;
    }

    // task to run the job on startup
    if (updateConfig.startupType != CollectionUpdateType.never) {
      map = new org.quartz.JobDataMap();
      map.put(UpdateType, updateConfig.startupType);
      map.put(Source, "startup");
      Date runTime = new Date(new Date().getTime() + startupWait); // wait startupWait before trigger
      SimpleTrigger startupTrigger = (SimpleTrigger) TriggerBuilder.newTrigger()
              .withIdentity(collectionName, "startup")
              .startAt(runTime)
              .forJob(updateJob)
              .usingJobData(map)
              .build();

      try {
        scheduler.scheduleJob(startupTrigger);
        logger.info("scheduleJob startup scan force={} for '{}' at {}", updateConfig.startupType.toString(), config.collectionName, runTime);
      } catch (Throwable e) {
        logger.error("scheduleJob failed to schedule startup Job for " + config, e);
        return;
      }
    }

    // task to run the job periodically, with rescan
    if (updateConfig.rescan != null) {
        map = new org.quartz.JobDataMap();
        map.put(UpdateType, updateConfig.updateType);
        map.put(Source, "rescan");
        CronTrigger rescanTrigger = TriggerBuilder.newTrigger()
                .withIdentity(collectionName, "rescan")
                .withSchedule(CronScheduleBuilder.cronSchedule(updateConfig.rescan))
                .forJob(updateJob)
                .usingJobData(map)
                .build();

      try {
    		scheduler.scheduleJob(rescanTrigger);
        logger.info("scheduleJob recurring scan for '{}' cronExpr={}", config.collectionName, updateConfig.rescan);
      } catch (Throwable e) {
        logger.error("scheduleJob failed to schedule cron Job", e);
        // e.printStackTrace();
      }
    }

    /* updating the proto dataset
    FeatureCollectionConfig.ProtoConfig pconfig = config.protoConfig;
    if (pconfig.change != null) {
      org.quartz.JobDataMap pmap = new org.quartz.JobDataMap();
      pmap.put(DCM_NAME, manager);
      map.put(LOGGER, logger);
      JobDetail protoJob = JobBuilder.newJob(ChangeProtoJob.class)
              .withIdentity(jobName, "UpdateProto")
              .usingJobData(pmap)
              .storeDurably()
              .build();

      try {
        CronTrigger protoTrigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName, "rereadProto")
                .withSchedule(CronScheduleBuilder.cronSchedule(pconfig.change))
                .build();
        scheduler.scheduleJob(protoJob, protoTrigger);
        if (logger != null)logger.info("Schedule proto update for '{}' cronExpr={}", config.collectionName, pconfig.change);

      } catch (Throwable e) {
        if (logger != null)logger.error("cronExecutor failed to schedule RereadProtoJob", e);
        // e.printStackTrace();
      }
    } */

  }

  public void shutdown() {
    if (scheduler == null) return;
    try {
      scheduler.shutdown(true);
      System.out.printf("Quartz scheduler shutdown%n");
    } catch (Throwable e) {
      fcLogger.error("Scheduler failed to shutdown", e);
      scheduler = null;
      //e.printStackTrace();
    }
  }

  /* Called by TDS collectionController when trigger is received externally
  public void triggerUpdate(String collectionName, CollectionUpdateType triggerType) {
    JobDataMap map = new org.quartz.JobDataMap();
    map.put(UpdateType, triggerType);
    Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(collectionName+"-trigger", triggerType.toString()) // dorky
            .forJob(collectionName, "UpdateCollection") // ??
            .usingJobData(map)
            .startNow()
            .build();

    try {
      // logger.debug("Trigger Update for {} type= {}", collectionName, triggerType);
      scheduler.scheduleJob(trigger);
    } catch (Throwable e) {
      startupLogger.error("triggerUpdate failed", e);
      // e.printStackTrace();
    }
  } */

  // do the work in a separate thread
  public static class UpdateCollectionJob implements org.quartz.Job {
    public UpdateCollectionJob() {
    }

    public void execute(JobExecutionContext context) throws JobExecutionException {
      EventBus eventBus = (EventBus) context.getJobDetail().getJobDataMap().get(EVENT_BUS);
      String collectionName = (String) context.getJobDetail().getJobDataMap().get(COLLECTION_NAME);
      org.slf4j.Logger loggerfc = (org.slf4j.Logger) context.getJobDetail().getJobDataMap().get(LOGGER);
      CollectionUpdateType type= (CollectionUpdateType) context.getTrigger().getJobDataMap().get(UpdateType);
      String source= (String) context.getTrigger().getJobDataMap().get(Source);
      String groupName = context.getTrigger().getKey().getGroup();

      try {
        eventBus.post( new CollectionUpdateEvent(type, collectionName, source));
        fcLogger.debug("CollectionUpdate post event {} on {}", type, collectionName);

      } catch (Throwable e) {
        if (loggerfc != null) loggerfc.error("UpdateCollectionJob.execute "+groupName+" failed collection=" + collectionName, e);
      }
    }
  }

  /* disabled LOOK
  public static class ChangeProtoJob implements org.quartz.Job {
    public ChangeProtoJob() {
    }

    public void execute(JobExecutionContext context) throws JobExecutionException {
      CollectionUpdateListener manager = (CollectionUpdateListener) context.getJobDetail().getJobDataMap().get(DCM_NAME);
      org.slf4j.Logger loggerfc = (org.slf4j.Logger) context.getJobDetail().getJobDataMap().get(LOGGER);

      try {
        if (loggerfc != null) loggerfc.info("ResetProto for {}", manager.getCollectionName());
        // manager.sendEvent(CollectionUpdateListener.TriggerType.resetProto);
      } catch (Throwable e) {
        if (loggerfc != null) loggerfc.error("ChangeProtoJob.execute failed collection=" + manager.getCollectionName(), e);
      }
    }
  } */
}
