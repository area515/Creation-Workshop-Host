package org.area515.resinprinter.job;

import java.io.File;
import java.util.Date;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.io.FilenameUtils;
import org.area515.util.DynamicJSonSettings;

import com.fasterxml.jackson.annotation.JsonFormat;

public class Printable {
	private String name;
	private String extension;
	private long size;
	private PrintFileProcessor<?,?> printFileProcessor;
	@JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm a z")
	private Date modifiedDate;
			
	public Printable(File printable, PrintFileProcessor<?,?> processor) {
		name = FilenameUtils.getBaseName(printable.getName());
		extension = FilenameUtils.getExtension(printable.getName());
		size = printable.length();
		printFileProcessor = processor;
		modifiedDate = new Date(printable.lastModified());
	}

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public long getSize() {
		return size;
	}
	public void setSize(long size) {
		this.size = size;
	}

	public PrintFileProcessor<?,?> getPrintFileProcessor() {
		return printFileProcessor;
	}
	public void setPrintFileProcessor(PrintFileProcessor<?,?> printFileProcessor) {
		this.printFileProcessor = printFileProcessor;
	}

	public String getExtension() {
		return extension;
	}
	public void setExtension(String extension) {
		this.extension = extension;
	}

	public Date getModifiedDate() {
		return modifiedDate;
	}
	public void setModifiedDate(Date modifiedDate) {
		this.modifiedDate = modifiedDate;
	}
}
