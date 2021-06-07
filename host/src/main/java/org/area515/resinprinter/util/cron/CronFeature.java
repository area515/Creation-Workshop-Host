package org.area515.resinprinter.util.cron;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.plugin.Feature;
import org.area515.util.DynamicJSonSettings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class CronFeature implements Feature {
    private static final Logger logger = LogManager.getLogger();

    private List<CronTask> taskList = new ArrayList<CronTask>();
	public static ScheduledExecutorService CRON_EXECUTOR = new ScheduledThreadPoolExecutor(10, new ThreadFactoryBuilder().setNameFormat("CronThread-%d").setDaemon(true).build());
	
	public static class CronTask implements Callable<Object> {
	    private URI uri;
		private String taskName;
		private Runnable runnable;
		private Callable<?> callable;
		private CronPredictor predictor;
		private ScheduledFuture<?> future;
		private boolean canceledTask;
		
		private String taskClassName;
		private String cronString;
		private DynamicJSonSettings taskSettings;
		
		public CronTask() {
		}

		@JsonProperty
		public DynamicJSonSettings getTaskSettings() {
			return taskSettings;
		}
		public void setTaskSettings(DynamicJSonSettings taskSettings) {
			this.taskSettings = taskSettings;
		}

		@JsonProperty
		public void setCronString(String cronString) {
			this.cronString = cronString;
		}
		public void setTaskName(String taskName) {
			this.taskName = taskName;
		}
	
		@JsonProperty
		public String getTaskName() {
			return taskName;
		}
		public String getCronString() {
			return cronString;
		}

		@JsonProperty
		public String getTaskClassName() {
			return taskClassName;
		}
		public void setTaskClassName(String taskClassName) {
			this.taskClassName = taskClassName;
		}

		private void initializeIfNecessary() throws RejectedExecutionException {
			//If we are already initialized, let's get out of here.
			if (predictor != null) {
				return;
			}
			
			predictor = new CronPredictor(cronString);
			
			if (taskClassName == null) {
				throw new RejectedExecutionException("Cron task:" + taskName + " doesn't have a taskClassName set");
			}
			
			try {
				Class<?> runnableOrCallableClass = Class.forName(taskClassName);
				Object runnableOrCallable = runnableOrCallableClass.newInstance();
				if (runnableOrCallable instanceof Runnable) {
					runnable = (Runnable)runnableOrCallable;
				} else if (runnableOrCallable instanceof Callable) {
					callable = (Callable<?>)runnableOrCallable;
				} else {
					throw new RejectedExecutionException(runnableOrCallable + " class:" + runnableOrCallableClass + " not an instance of Runnable or Callable");
				}
				
				HashMap<String, Object> settings = getTaskSettings() == null?new HashMap<String, Object>():getTaskSettings().getSettings();
				settings.put("uri", uri);
				BeanUtils.populate(runnableOrCallable, settings);
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
				logger.error("Couldn't create class:" + taskClassName, e);
			}
		}

		@SuppressWarnings("unchecked")
		public synchronized void scheduleNextRun() throws RejectedExecutionException {
			if (canceledTask) {
				throw new RejectedExecutionException("Task cannot be executed again since it has been cancelled.");
			}
			
			initializeIfNecessary();
			
			long nextExecution = predictor.nextMatchingTime();
			future = CRON_EXECUTOR.schedule(this, nextExecution - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
			
			logger.info("Scheduled task:" + taskName + " for:" + new Date(nextExecution));
		}

		public synchronized boolean cancel() {
			canceledTask = true;
			return future.cancel(true);
		}
		
		@Override
		public Object call() throws Exception {
			Object returnObject;
			try {
				if (runnable != null) {
					runnable.run();
					returnObject = Void.TYPE;
				} else if (callable != null){
					returnObject = callable.call();
				} else {
					throw new RejectedExecutionException("Must implement either a runnable or a callable.");
				}
				scheduleNextRun();
				return returnObject;
			} catch (RejectedExecutionException e) {
			    logger.error("I will NEVER attempt to execute this task again:" + taskName, e);
			    throw e;
			} catch (Exception e) {
			    logger.error("I will attempt to execute this task again:" + taskName, e);
			    throw e;
			} catch (Throwable t) {
			    logger.error("Can't recover, I will NEVER attempt to execute this task again:" + taskName, t);
			    throw t;
			}
		}
		
		public String toString() {
			return taskName;
		}
	}
	
	@Override
	public void start(URI uri, String cronTasks) throws Exception {
		if (cronTasks == null) {
			logger.info("No cron tasks were setup");
			return;
		}
		
		ObjectMapper mapper = new ObjectMapper(new JsonFactory());
		try {
			CronTask[] cronTask = mapper.readValue(cronTasks, new TypeReference<CronTask[]>(){});
			for (CronTask currentTask : cronTask) {
				currentTask.uri = uri;
			}
			Collections.addAll(taskList, cronTask);
		} catch (IOException e) {
			throw new IllegalArgumentException(cronTasks + " didn't parse correctly.", e);
		}

		for (CronTask task : taskList) {
			try {
				task.scheduleNextRun();
			} catch (RejectedExecutionException e) {
				logger.error("Couldn't schedule task:" + task.getTaskName(), e);
			}
		}
	}

	@Override
	public void stop() {
		for (CronTask task : taskList) {
			task.cancel();
		}
	}
}
