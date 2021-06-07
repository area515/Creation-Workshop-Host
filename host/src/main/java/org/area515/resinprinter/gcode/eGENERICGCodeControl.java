package org.area515.resinprinter.gcode;

import java.io.IOException;

import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.services.TestingResult;
import org.area515.util.TemplateEngine;

import freemarker.template.TemplateException;

public class eGENERICGCodeControl extends PrinterController {
    public eGENERICGCodeControl(Printer printer) {
    	super(printer);
    }
    
    public TestingResult testTemplate(Printer printer, String scriptName, String commands) {
		try {
			PrintJob job = TemplateEngine.buildStubJob(printer);
			String returnValue = TemplateEngine.buildData(job, getPrinter(), commands);
			return new TestingResult(returnValue);
		} catch (JobManagerException | IOException e) {
			TestingResult result = new TestingResult(e.getMessage(), -1);
			return result;
		} catch (TemplateException e) {
			TestingResult result = new TestingResult(e.getMessage(), e.getLineNumber());
			return result;
		}
    }

    public String executeSetAbsolutePositioning() {
    	return executeSingleCommand("G91\r\n");
    }
    public String executeSetRelativePositioning() {
    	return executeSingleCommand("G91\r\n");
    }
    public String executeMoveX(double dist) {
    	return executeSingleCommand(String.format("G1 X%1.3f\r\n", dist));
    }
    public String executeMoveY(double dist) {
    	return executeSingleCommand(String.format("G1 Y%1.3f\r\n", dist));
    }
    public String executeMoveZ(double dist) {
    	return executeSingleCommand(String.format("G1 Z%1.3f\r\n", dist));
    }
    public String executeMotorsOn() {
    	return executeSingleCommand("M17\r\n");
    }
    public String executeMotorsOff() {
    	return executeSingleCommand("M18\r\n");
    }
    public String executeXHome() {
        return executeSingleCommand("G28 X\r\n");
    }
    public String executeYHome() {
        return executeSingleCommand("G28 Y\r\n");
    }
    public String executeZHome() {
        return executeSingleCommand("G28 Z\r\n");
    }
    public String executeHomeAll() {
        return executeSingleCommand("G28\r\n");
    }
}