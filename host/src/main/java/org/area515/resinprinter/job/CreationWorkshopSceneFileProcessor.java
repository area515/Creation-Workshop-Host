package org.area515.resinprinter.job;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.exception.SliceHandlingException;
import org.area515.resinprinter.job.render.CurrentImageRenderer;
import org.area515.resinprinter.job.render.RenderingContext;
import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.server.HostProperties;
import org.area515.resinprinter.twodim.SimpleImageRenderer;
import org.area515.util.IOUtilities;

import se.sawano.java.text.AlphanumericComparator;

public class CreationWorkshopSceneFileProcessor extends AbstractPrintFileProcessor<Object,Object> implements Previewable {
	private static final Logger logger = LogManager.getLogger();
	
	@Override
	public String[] getFileExtensions() {
		return new String[]{"cws", "zip"};
	}
	
	@Override
	public boolean acceptsFile(File processingFile) {
		//TODO: we shouldn't except all zip files only those that have embedded gif/jpg/png information.
		if (processingFile.getName().toLowerCase().endsWith(".zip") || processingFile.getName().toLowerCase().endsWith(".cws")) {
			if (zipHasGCode(processingFile)) {
				// if the zip has gcode, treat it as a CW scene
				logger.info("Accepting new printable {} as a {}", processingFile.getName(), this.getFriendlyName());
				return true;
			}
		}
		return false;
	}
	
	@Override
	public CurrentImageRenderer createRenderer(DataAid aid, Object imageIndexToBuild) {
		return new SimpleImageRenderer(aid, this, imageIndexToBuild);
	}

	protected SortedMap<String, File> findImages(File jobFile) throws JobManagerException {
		String [] extensions = {"png", "PNG"};
		boolean recursive = true;
		
		Collection<File> files =
				FileUtils.listFiles(buildExtractionDirectory(jobFile.getName()),
				extensions, recursive);

		TreeMap<String, File> images = new TreeMap<>(new AlphanumericComparator());

		for (File file : files) {
			if (file.getPath().contains("__MACOSX") && file.getName().startsWith(".")) {
				continue;
			}
			
			images.put(file.getName(), file);
		}
		
		return images;
	}

	@Override
	public BufferedImage renderPreviewImage(DataAid dataAid) throws SliceHandlingException {
		try {
			prepareEnvironment(dataAid.printJob.getJobFile(), dataAid.printJob);
			
			SortedMap<String, File> imageFiles = findImages(dataAid.printJob.getJobFile());
			
			dataAid.printJob.setTotalSlices(imageFiles.size());
			Iterator<File> imgIter = imageFiles.values().iterator();
	
			// Preload first image then loop
			int sliceIndex = dataAid.customizer.getNextSlice();
			while (imgIter.hasNext() && sliceIndex > 0) {
				sliceIndex--;
				imgIter.next();
			}
			
			if (!imgIter.hasNext()) {
				throw new IOException("No Image Found for index:" + dataAid.customizer.getNextSlice());
			}
			File imageFile = imgIter.next();
			RenderingContext stdImage = startImageRendering(dataAid, imageFile).get();
			return stdImage.getPrintableImage();
		} catch (IOException | JobManagerException | InterruptedException | ExecutionException e) {
			throw new SliceHandlingException(e);
		}
	}

	@Override
	public Double getBuildAreaMM(PrintJob processingFile) {
		return null;
	}

	private File buildImageFile(File gCodeFile, int padLength, int index) {
		String imageNumber = String.format("%0" + padLength + "d", index);
		String imageFilename = FilenameUtils.removeExtension(gCodeFile.getName()) + imageNumber + ".png";
		File imageFile = new File(gCodeFile.getParentFile(), imageFilename);
		return imageFile;
	}
	
	@Override
	public JobStatus processFile(final PrintJob printJob) throws Exception {
		File gCodeFile = findGcodeFile(printJob.getJobFile());
		DataAid aid = initializeJobCacheWithDataAid(printJob);
		
		Printer printer = printJob.getPrinter();
		BufferedReader stream = null;
		long startOfLastImageDisplay = -1;
		try {
			logger.info("Parsing file:{}", gCodeFile);
			int padLength = determinePadLength(gCodeFile);
			File imageFileToRender = buildImageFile(gCodeFile, padLength, 0);
			Future<RenderingContext> nextConFuture = startImageRendering(aid, imageFileToRender);
			aid.cache.setCurrentRenderingPointer(imageFileToRender);

			int imageIndexCached = 0;
			
			stream = new BufferedReader(new FileReader(gCodeFile));
			String currentLine;
			Integer sliceCount = null;
			Pattern slicePattern = Pattern.compile("\\s*;\\s*<\\s*Slice\\s*>\\s*(\\d+|blank)\\s*", Pattern.CASE_INSENSITIVE);
			Pattern liftSpeedPattern = Pattern.compile(   "\\s*;\\s*\\(?\\s*Z\\s*Lift\\s*Feed\\s*Rate\\s*=\\s*([\\d\\.]+)\\s*(?:[Mm]{2}?/[Ss])?\\s*\\)?\\s*", Pattern.CASE_INSENSITIVE);
			Pattern liftDistancePattern = Pattern.compile("\\s*;\\s*\\(?\\s*Lift\\s*Distance\\s*=\\s*([\\d\\.]+)\\s*(?:[Mm]{2})?\\s*\\)?\\s*", Pattern.CASE_INSENSITIVE);
			Pattern sliceCountPattern = Pattern.compile("\\s*;\\s*Number\\s*of\\s*Slices\\s*=\\s*(\\d+)\\s*", Pattern.CASE_INSENSITIVE);
			
			//We can't set these values, that means they aren't set to helpful values when this job starts
			//data.printJob.setExposureTime(data.inkConfiguration.getExposureTime());
			//data.printJob.setZLiftDistance(data.slicingProfile.getLiftFeedRate());
			//data.printJob.setZLiftSpeed(data.slicingProfile.getLiftDistance());

			while ((currentLine = stream.readLine()) != null && printer.isPrintActive()) {
					Matcher matcher = slicePattern.matcher(currentLine);
					if (matcher.matches()) {
						if (sliceCount == null) {
							throw new IllegalArgumentException("No 'Number of Slices' line in gcode file");
						}

						if (matcher.group(1).toUpperCase().equals("BLANK")) {
							logger.info("Show Blank");
							printer.showBlankImage();
							
							//This is the perfect time to wait for a pause if one is required.
							printer.waitForPauseIfRequired(this, aid);
						} else {
							if (startOfLastImageDisplay > -1) {
					//printJob.setCurrentSliceTime(System.currentTimeMillis() - startOfLastImageDisplay);
								printJob.completeRenderingSlice(System.currentTimeMillis() - startOfLastImageDisplay, null);
							}
							startOfLastImageDisplay = System.currentTimeMillis();
							RenderingContext context = nextConFuture.get();
							int incoming = Integer.parseInt(matcher.group(1));
							File currentImage = buildImageFile(gCodeFile, padLength, incoming);
							aid.cache.setCurrentRenderingPointer(currentImage);
							
							//This is to prevent a miscache in the event that someone built this file as 1 based or some other strange configuration.
							if (incoming != imageIndexCached) {
								nextConFuture = startImageRendering(aid, currentImage);
							}
							imageIndexCached = incoming + 1;
							
							imageFileToRender = buildImageFile(gCodeFile, padLength, incoming + 1);
							nextConFuture = startImageRendering(aid, imageFileToRender);
							//BufferedImage newImage = applyImageTransforms(aid, context.getScriptEngine(), context.getPrintableImage());
							logger.info("Show picture: {}", incoming);
							
							//Notify the client that the printJob has increased the currentSlice
							NotificationManager.jobChanged(printer, printJob);

							printer.showImage(context.getPrintableImage(), true);
						}
						continue;
					}
					
					/*matcher = delayPattern.matcher(currentLine);
					if (matcher.matches()) {
						try {
							int sleepTime = Integer.parseInt(matcher.group(1));
							if (printJob.isExposureTimeOverriden()) {
								sleepTime = printJob.getExposureTime();
							} else {
								printJob.setExposureTime(sleepTime);
							}
							logger.info("Sleep:{}", sleepTime);
							Thread.sleep(sleepTime);
							logger.info("Sleep complete");
						} catch (InterruptedException e) {
							logger.error("Interrupted while waiting for exposure to complete.", e);
						}
						continue;
					}*/
					
					matcher = sliceCountPattern.matcher(currentLine);
					if (matcher.matches()) {
						sliceCount = Integer.parseInt(matcher.group(1));
						printJob.setTotalSlices(sliceCount);
						logger.info("Found:{} slices", sliceCount);
						continue;
					}
					
					matcher = liftSpeedPattern.matcher(currentLine);
					if (matcher.matches()) {
						double foundLiftSpeed = Double.parseDouble(matcher.group(1));
						if (printJob.isZLiftSpeedOverriden()) {
							logger.info("Override: LiftDistance:{} overrided to:{}" , String.format("%1.3f", foundLiftSpeed), String.format("%1.3f", printJob.getZLiftSpeed()));
						} else {
							printJob.setZLiftSpeed(foundLiftSpeed);
							logger.info("Found: LiftSpeed of:" + String.format("%1.3f", foundLiftSpeed));
						}
						continue;
					}
					
					matcher = liftDistancePattern.matcher(currentLine);
					if (matcher.matches()) {
						double foundLiftDistance = Double.parseDouble(matcher.group(1));
						if (printJob.isZLiftDistanceOverriden()) {
							logger.info("Override: LiftDistance:{} overrided to:{}", String.format("%1.3f", foundLiftDistance), String.format("%1.3f", printJob.getZLiftDistance()));
						} else {
							printJob.setZLiftDistance(foundLiftDistance);
							logger.info("Found: LiftDistance of:{}", String.format("%1.3f", foundLiftDistance));
						}
						continue;
					}
					
					/*matcher = gCodePattern.matcher(currentLine);
					if (matcher.matches()) {
						String gCode = matcher.group(1).trim();
						logger.info("Send GCode:{}", gCode);

						for (int t = 0; t < 3; t++) {
							gCode = printer.getGCodeControl().sendGcodeAndRespectPrinter(printJob, gCode);
							if (gCode != null) {
								break;
							}
							logger.info("Printer timed out:{}", t);
						}
						logger.info("Printer Response:{}", gCode);
						continue;
					}*/
					
					// print out comments
					//logger.info("Ignored line:{}", currentLine);
					printer.getPrinterController().executeCommands(printJob, currentLine, true);
			}
			
			//This is a special case where the gcode footer wasn't executed since the user cancelled the job and it didn't reach the end of the gcode file.
			if (printer.getStatus() == JobStatus.Cancelling) {
				performFooter(aid);
			}
			
			return printer.isPrintActive()?JobStatus.Completed:printer.getStatus();
		} catch (IOException e) {
			logger.error("Error occurred while processing file.", e);
			throw e;
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
				}
			}
			aid.cache.clearCache(Boolean.TRUE);
			clearDataAid(printJob);
		}
	}
	
	public static File buildExtractionDirectory(String archive) {
		return Paths.get(HostProperties.Instance().getWorkingDir().toString(), archive).toFile();
	}

	private void deleteDirectory(File extractDirectory) throws JobManagerException {
		String unable = "Unable to delete directory (.*)[.]";
		boolean deletePerformed = false;
		int attemptsToDelete = 0;
		List<IOException> cantDelete = new ArrayList<>();
		do {
			try {
				attemptsToDelete++;
				FileUtils.deleteDirectory(extractDirectory);
				deletePerformed = true;
			} catch (IOException e) {
				if (e.getMessage() != null) {
					Pattern pattern = Pattern.compile(unable);
					Matcher matcher = pattern.matcher(e.getMessage());
					if (matcher.matches()) {
						logger.debug(() -> {
								String[] output = IOUtilities.executeNativeCommand(new String[]{"ls", "-al", matcher.group(1)}, null);
								StringBuilder builder = new StringBuilder();
								for (String outLine :output) {
									builder.append(outLine + "\n");
								}
								return builder.toString();
							}
						);
					}
				}
				cantDelete.add(e);
				deletePerformed = false;
			}
		} while (!deletePerformed && attemptsToDelete < 3);
		
		if (!deletePerformed) {
			if (cantDelete.size() > 1) {
				for (IOException e : cantDelete) {
					logger.error("Error List", e);
				}
			}
			throw new JobManagerException("Couldn't clean directory for new job:" + extractDirectory, cantDelete.get(0));
		}
	}
	
	@Override
	public void prepareEnvironment(File processingFile, PrintJob printJob) throws JobManagerException {
		List<PrintJob> printJobs = PrintJobManager.Instance().getJobsByFilename(processingFile.getName());
		for (PrintJob currentJob : printJobs) {
			if (!currentJob.getId().equals(printJob.getId()) && currentJob.isPrintInProgress()) {
				throw new JobManagerException("It currently isn't possible to print more than 1 " + getFriendlyName() + " file at once.");
			}
		}
		
		synchronized (processingFile.getAbsolutePath().intern()) {
			File extractDirectory = buildExtractionDirectory(processingFile.getName());
			long oldCRC = 0;
			File crc32File = new File(extractDirectory, "CRC32");
			if (crc32File.exists()) {
				try {
					oldCRC = Long.parseLong(FileUtils.readFileToString(crc32File));
				} catch (IOException e) {
					throw new JobManagerException("Couldn't compute CRC for:" + processingFile, e);
				}
			}
	
			try {
				long newCRC = FileUtils.checksumCRC32(processingFile);
				if (oldCRC == newCRC) {
					logger.info("CRC checks match, reusing old structure:" + processingFile);
					return;
				}
	
				deleteDirectory(extractDirectory);
				extractDirectory.mkdirs();
				FileUtils.writeStringToFile(crc32File, newCRC + "");
			} catch (IOException e) {
				logger.error(e);
			}
	
			try {
				unpackDir(processingFile);
			} catch (IOException e) {
				throw new JobManagerException("Couldn't unpack new job:" + processingFile + " into working directory:" + extractDirectory + " due to:" + e.getMessage(), e);
			}
		}
	}

	@Override
	public void cleanupEnvironment(File processingFile) throws JobManagerException {
		File extractDirectory = buildExtractionDirectory(processingFile.getName());
		if (extractDirectory.exists()) {
			deleteDirectory(extractDirectory);
		}
	}
	
	protected boolean zipHasGCode(File zipFile) {
		ZipFile zip = null;
		
		try {
			zip = new ZipFile(zipFile, Charset.forName("CP437"));
			return zip.stream().anyMatch(z -> z.getName().toLowerCase().endsWith("gcode"));
		} catch (IOException e) {
			logger.error("Unable to open uploaded zip file", e);
		} finally {
			if (zip != null) {
				try {
					zip.close();
				} catch (IOException e) {
					logger.warn("Unable to close uploaded zip file", e);
				}
			}
		}
		
		return false;
		
	}
	
	
	private File findGcodeFile(File jobFile) throws JobManagerException{
	
            String[] extensions = {"gcode"};
            boolean recursive = true;
            
            //
            // Finds files within a root directory and optionally its
            // subdirectories which match an array of extensions. When the
            // extensions is null all files will be returned.
            //
            // This method will returns matched file as java.io.File
            //
            List<File> files = new ArrayList<File>(FileUtils.listFiles(buildExtractionDirectory(jobFile.getName()), extensions, recursive));
            Iterator<File> iter = files.iterator();
            for (File currentFile = iter.next(); iter.hasNext(); currentFile = iter.next()) {
    			if (currentFile.getPath().contains("__MACOSX") && currentFile.getName().startsWith(".")) {
    				iter.remove();
    			}
    		}
            
           if (files.size() > 1){
            	throw new JobManagerException("More than one gcode file exists in print directory:" + files);
            }else if (files.size() == 0){
            	throw new JobManagerException("Gcode file was not found. Did you include the Gcode when you exported your scene?");
            }
           
           return files.get(0);
	}
	
	private void unpackDir(File jobFile) throws IOException, JobManagerException {
		ZipFile zipFile = null;
		InputStream in = null;
		OutputStream out = null;
		File extractDirectory = buildExtractionDirectory(jobFile.getName());
		try {
			zipFile = new ZipFile(jobFile, Charset.forName("CP437"));
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				File entryDestination = new File(extractDirectory, entry.getName());
				entryDestination.getParentFile().mkdirs();
				if (entry.isDirectory())
					entryDestination.mkdirs();
				else {
					in = zipFile.getInputStream(entry);
					out = new FileOutputStream(entryDestination);
					IOUtils.copy(in, out);
					IOUtils.closeQuietly(in);
					IOUtils.closeQuietly(out);
				}
			}
			String basename = FilenameUtils.removeExtension(jobFile.getName());
			logger.info("BaseName: {}", FilenameUtils.removeExtension(basename));
			//findGcodeFile(jobFile);
		} finally {
			zipFile.close();
		}
	}
	
	public int determinePadLength(File gCode) throws FileNotFoundException {
		File currentFile = null;
		for (int t = 1; t < 10; t++) {
			currentFile = new File(gCode.getParentFile(), FilenameUtils.removeExtension(gCode.getName()) + String.format("%0" + t + "d", 0) + ".png");
			if (currentFile.exists()) {
				return t;
			}
		}
		
		throw new FileNotFoundException("Couldn't find any files to determine image index pad.");
	}

	@Override
	public Object getGeometry(PrintJob printJob) throws JobManagerException {
		throw new JobManagerException("You can't get geometry from this type of file");
	}

	@Override
	public Object getErrors(PrintJob printJob) throws JobManagerException {
		throw new JobManagerException("You can't get error geometry from this type of file");
	}

	@Override
	public String getFriendlyName() {
		return "Creation Workshop Scene";
	}

	@Override
	public boolean isThreeDimensionalGeometryAvailable() {
		return false;
	}
}
