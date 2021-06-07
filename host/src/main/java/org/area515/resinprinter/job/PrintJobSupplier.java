package org.area515.resinprinter.job;

import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.server.HostProperties;

public class PrintJobSupplier implements Supplier<JobStatus> {
	private static final Logger logger = LogManager.getLogger();
	private PrintJob printJob = null;
	private Printer printer;
	private PrintFileProcessor<?,?> processor;
	
	public PrintJobSupplier(PrintJob printJob, Printer printer) {
		this.printJob = printJob;
		this.printer = printer;
		for (PrintFileProcessor<?,?> currentProcessor : HostProperties.Instance().getPrintFileProcessors()) {
			if (currentProcessor.acceptsFile(printJob.getJobFile())) {
				processor = currentProcessor;
			}
		}
	}
	
	public PrintFileProcessor<?,?> getPrintFileProcessor() {
		return processor;
	}
	
	@Override
	public JobStatus get() {
		try {
			logger.info("Starting:{} on Printer:{} executing on Thread:{}", printJob, printer, Thread.currentThread().getName());
			printer.setStatus(JobStatus.Printing);
			printJob.setStartTime(System.currentTimeMillis());
			NotificationManager.jobChanged(printer, printJob);
			processor.prepareEnvironment(printJob.getJobFile(), printJob);
			JobStatus status = processor.processFile(printJob);
			if (status == JobStatus.Cancelling) {
				status = JobStatus.Cancelled;
			}
			printer.setStatus(status);
			return status;
		} catch (Exception e) {
			logger.error("Execution failure.", e);
			//I really think this needs to throw a new RuntimeException instead of this Failed jobstatus that way we let the proper error message get back to the user
			throw new RuntimeException("Execution failure.", e);
			//This is what it used to do:
			//return JobStatus.Failed;
		}
	}
}